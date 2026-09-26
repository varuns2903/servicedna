"""Checks the service's own health endpoint on an interval and reports the result to ServiceDNA,
which registers the service on first contact. Its HTTP calls are made with tracing suppressed so
heartbeats don't show up as the service's traffic."""

from __future__ import annotations

import json
import logging
import threading
import time
import urllib.error
import urllib.request

from opentelemetry import context

from .config import Config

log = logging.getLogger("servicedna")
_started = False


def start(config: Config) -> None:
    global _started
    if _started or not config.local_health_url or config.heartbeat_seconds <= 0:
        return
    _started = True
    thread = threading.Thread(target=_loop, args=(config,), name="servicedna-heartbeat", daemon=True)
    thread.start()


def _loop(config: Config) -> None:
    while True:
        time.sleep(config.heartbeat_seconds)
        token = context.attach(context.set_value(context._SUPPRESS_INSTRUMENTATION_KEY, True))
        try:
            _beat(config)
        except Exception as e:  # never let the heartbeat thread die
            log.warning("[servicedna] heartbeat failed: %s", e)
        finally:
            context.detach(token)


def _beat(config: Config) -> None:
    start = time.monotonic()
    status, message = "DOWN", "health check failed"
    try:
        with urllib.request.urlopen(config.local_health_url, timeout=5) as res:
            body = res.read()
            elapsed_ms = (time.monotonic() - start) * 1000
            status = "DEGRADED" if elapsed_ms > config.degraded_ms else "HEALTHY"
            message = _message(body) or f"HTTP {res.status}"
    except urllib.error.HTTPError as e:
        message = _message(e.read()) or f"HTTP {e.code}"
    except Exception as e:
        message = str(e) or type(e).__name__
    latency_ms = int((time.monotonic() - start) * 1000)

    payload = json.dumps(
        {
            "status": status,
            "latencyMs": latency_ms,
            "message": message[:500],
            "service": config.service_name,
            "environment": config.environment,
        }
    ).encode()
    request = urllib.request.Request(
        f"{config.url}/api/v1/ping",
        data=payload,
        headers={"Content-Type": "application/json", "X-API-Key": config.key},
        method="POST",
    )
    try:
        urllib.request.urlopen(request, timeout=5).close()
    except urllib.error.HTTPError as e:
        log.warning("[servicedna] heartbeat rejected: HTTP %s", e.code)


def _message(body: bytes):
    try:
        data = json.loads(body)
        return str(data.get("message") or data.get("status") or "") or None
    except (ValueError, AttributeError):
        return None
