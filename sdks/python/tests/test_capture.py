import json

import pytest
from fastapi import FastAPI, HTTPException, Request
from fastapi.testclient import TestClient
from opentelemetry import propagate, trace
from opentelemetry.baggage.propagation import W3CBaggagePropagator
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.propagators.composite import CompositePropagator
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import SpanKind
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from servicedna import bodies as sdna

exporter = InMemorySpanExporter()
provider = TracerProvider()
provider.add_span_processor(SimpleSpanProcessor(exporter))
trace.set_tracer_provider(provider)
propagate.set_global_textmap(CompositePropagator([TraceContextTextMapPropagator(), W3CBaggagePropagator()]))

app = FastAPI()


@app.post("/charge")
async def charge(request: Request):
    body = await request.json()
    sdna.capture("computed.total", {"amount": body["amount"] * 2})
    if body.get("fail"):
        raise HTTPException(status_code=503, detail="processor unavailable")
    if body.get("crash"):
        raise RuntimeError("bug")
    if body.get("reject"):
        raise HTTPException(status_code=402, detail="declined")
    return {"status": "CAPTURED", "amount": body["amount"], "apiKey": "secret"}


FastAPIInstrumentor.instrument_app(
    app,
    server_request_hook=sdna.server_request_hook,
    client_request_hook=sdna.client_request_hook,
    client_response_hook=sdna.client_response_hook,
)
client = TestClient(app)


def server_span():
    return next(s for s in exporter.get_finished_spans() if s.kind == SpanKind.SERVER)


@pytest.fixture(autouse=True)
def reset():
    exporter.clear()


def test_records_bodies_of_a_capture_run_masking_credentials():
    client.post(
        "/charge",
        json={"amount": 59, "password": "hunter2"},
        headers={
            "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "baggage": "sdna.run=run_1,sdna.capture=1",
        },
    )
    attrs = server_span().attributes
    assert json.loads(attrs["sdna.request.body"]) == {"amount": 59, "password": "[masked]"}
    assert json.loads(attrs["sdna.response.body"]) == {"status": "CAPTURED", "amount": 59, "apiKey": "[masked]"}
    assert attrs["sdna.captured"] is True


def test_capture_records_computed_values_on_the_current_span():
    client.post("/charge", json={"amount": 5}, headers={"baggage": "sdna.capture=1"})
    captured = [s.attributes.get("sdna.capture.computed.total") for s in exporter.get_finished_spans()]
    assert '{"amount":10}' in [c for c in captured if c]


def test_leaves_ordinary_requests_alone():
    client.post("/charge", json={"amount": 1})
    attrs = server_span().attributes
    assert "sdna.request.body" not in attrs
    assert "sdna.response.body" not in attrs
    assert all("sdna.capture.computed.total" not in s.attributes for s in exporter.get_finished_spans())


def test_with_capture_on_error_only_failed_requests_carry_their_bodies(monkeypatch):
    monkeypatch.setenv("SERVICEDNA_CAPTURE_ON_ERROR", "true")

    client.post("/charge", json={"amount": 1})
    client.post("/charge", json={"amount": 2, "reject": True})
    assert all("sdna.request.body" not in s.attributes for s in exporter.get_finished_spans())

    exporter.clear()
    client.post("/charge", json={"amount": 3, "fail": True, "password": "x"})
    attrs = server_span().attributes
    assert json.loads(attrs["sdna.request.body"]) == {"amount": 3, "fail": True, "password": "[masked]"}
    assert json.loads(attrs["sdna.response.body"]) == {"detail": "processor unavailable"}
    assert attrs["sdna.captured_on_error"] is True
    assert "sdna.captured" not in attrs


def test_capture_on_error_includes_unhandled_exceptions(monkeypatch):
    monkeypatch.setenv("SERVICEDNA_CAPTURE_ON_ERROR", "true")
    TestClient(app, raise_server_exceptions=False).post("/charge", json={"amount": 4, "crash": True})
    attrs = server_span().attributes
    assert json.loads(attrs["sdna.request.body"]) == {"amount": 4, "crash": True}
    assert attrs["sdna.captured_on_error"] is True
