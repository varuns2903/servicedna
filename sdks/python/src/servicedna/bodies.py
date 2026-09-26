"""Request/response body capture for ServiceDNA. Requests carrying the baggage entry
``sdna.capture=1`` (test runs) are always captured. With ``SERVICEDNA_CAPTURE_ON_ERROR=true``,
every other request's bodies are held until its response and recorded only if it failed (5xx) —
successful traffic ships no payloads. Bodies are masked and truncated before being recorded on the
request's server span."""

from __future__ import annotations

import json
import os
import re

from opentelemetry import baggage, trace

MAX_BYTES = int(os.getenv("SERVICEDNA_CAPTURE_MAX_BYTES", "16384"))
_SENSITIVE = re.compile(r"pass(word|wd)?|secret|token|api[-_.]?key|authorization|cookie|session|card|cvv|ssn", re.I)
_SCOPE_KEY = "servicedna.capture"


def capture_on_error() -> bool:
    return os.getenv("SERVICEDNA_CAPTURE_ON_ERROR", "").lower() in ("1", "true", "yes")


def capture_requested() -> bool:
    return baggage.get_baggage("sdna.capture") == "1"


def redact(text: str) -> str:
    """Masks credential-like fields in JSON; other text is kept. Always truncates."""
    try:
        text = json.dumps(_mask(json.loads(text)), separators=(",", ":"))
    except (ValueError, TypeError):
        pass
    return text if len(text) <= MAX_BYTES else text[:MAX_BYTES] + "…[truncated]"


def _mask(value):
    if isinstance(value, dict):
        return {k: "[masked]" if _SENSITIVE.search(str(k)) else _mask(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_mask(v) for v in value]
    return value


def capture(name: str, value) -> None:
    """Records a value computed inside the service on the current span, for test runs:
    ``servicedna.capture("order.total", total)``. Does nothing outside a capture run."""
    if not capture_requested():
        return
    span = trace.get_current_span()
    if span.is_recording():
        text = value if isinstance(value, str) else json.dumps(value, default=str)
        span.set_attribute(f"sdna.capture.{name}", redact(text))


# ASGI instrumentation hooks (FastAPI, Starlette). The receive/send hooks run on the
# instrumentation's own receive/send spans, so the server span is remembered on the ASGI scope.


def server_request_hook(span, scope):
    if span is None or not span.is_recording():
        return
    if capture_requested():
        scope[_SCOPE_KEY] = {"span": span, "on_error": False, "status": None, "request": bytearray(), "response": bytearray()}
    elif capture_on_error():
        scope[_SCOPE_KEY] = {"span": span, "on_error": True, "status": None, "request": bytearray(), "response": bytearray()}


def client_request_hook(span, scope, message):
    state = scope.get(_SCOPE_KEY)
    if state is not None and message.get("type") == "http.request":
        _append(state["request"], message.get("body", b""))


def client_response_hook(span, scope, message):
    state = scope.get(_SCOPE_KEY)
    if state is None:
        return
    if message.get("type") == "http.response.start":
        state["status"] = message.get("status")
        return
    if message.get("type") != "http.response.body":
        return
    _append(state["response"], message.get("body", b""))
    if not message.get("more_body", False):
        scope.pop(_SCOPE_KEY, None)
        server_span = state["span"]
        if state["on_error"]:
            if (state["status"] or 0) < 500:
                return
            server_span.set_attribute("sdna.captured_on_error", True)
        else:
            server_span.set_attribute("sdna.captured", True)
        if state["request"]:
            server_span.set_attribute("sdna.request.body", redact(state["request"].decode("utf-8", "replace")))
        if state["response"]:
            server_span.set_attribute("sdna.response.body", redact(state["response"].decode("utf-8", "replace")))


def _append(buffer: bytearray, chunk: bytes) -> None:
    if chunk and len(buffer) <= MAX_BYTES:
        buffer.extend(chunk)


def instrument_fastapi() -> bool:
    """Instruments FastAPI with body capture. Returns False if FastAPI instrumentation isn't installed."""
    try:
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    except ImportError:
        return False
    FastAPIInstrumentor().instrument(
        server_request_hook=server_request_hook,
        client_request_hook=client_request_hook,
        client_response_hook=client_response_hook,
    )
    return True
