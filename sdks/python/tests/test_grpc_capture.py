import json

import grpc
import pytest
from grpc_health.v1 import health_pb2, health_pb2_grpc
from opentelemetry import baggage, context, trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from servicedna import grpc_capture

exporter = InMemorySpanExporter()
provider = TracerProvider()
provider.add_span_processor(SimpleSpanProcessor(exporter))
tracer = provider.get_tracer("test")


class Health(health_pb2_grpc.HealthServicer):
    async def Check(self, request, context):
        if request.service == "broken":
            await context.abort(grpc.StatusCode.UNAVAILABLE, "down")
        return health_pb2.HealthCheckResponse(status=health_pb2.HealthCheckResponse.SERVING)


class SpanAndBaggage(grpc.aio.ServerInterceptor):
    """Stands in for OpenTelemetry's server interceptor: a server span, with the caller's baggage."""

    def __init__(self, capture):
        self.capture = capture

    async def intercept_service(self, continuation, details):
        handler = await continuation(details)
        behavior = handler.unary_unary

        async def unary_unary(request, ctx):
            token = context.attach(baggage.set_baggage("sdna.capture", "1") if self.capture else context.get_current())
            try:
                with tracer.start_as_current_span("grpc.health.v1.Health/Check", kind=trace.SpanKind.SERVER):
                    return await behavior(request, ctx)
            finally:
                context.detach(token)

        return grpc.unary_unary_rpc_method_handler(
            unary_unary, request_deserializer=handler.request_deserializer, response_serializer=handler.response_serializer
        )


async def check(service: str, capture: bool):
    server = grpc.aio.server(interceptors=[SpanAndBaggage(capture), grpc_capture.aio_server_interceptor()])
    health_pb2_grpc.add_HealthServicer_to_server(Health(), server)
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    try:
        async with grpc.aio.insecure_channel(f"127.0.0.1:{port}") as channel:
            try:
                await health_pb2_grpc.HealthStub(channel).Check(health_pb2.HealthCheckRequest(service=service))
            except grpc.aio.AioRpcError:
                pass
    finally:
        await server.stop(None)
    return next(s for s in exporter.get_finished_spans() if s.kind == trace.SpanKind.SERVER).attributes


@pytest.fixture(autouse=True)
def reset(monkeypatch):
    exporter.clear()
    monkeypatch.delenv("SERVICEDNA_CAPTURE_ON_ERROR", raising=False)


@pytest.mark.asyncio
async def test_a_capture_run_records_the_messages():
    attrs = await check("payments", capture=True)
    assert json.loads(attrs["sdna.request.body"]) == {"service": "payments"}
    assert json.loads(attrs["sdna.response.body"]) == {"status": "SERVING"}
    assert attrs["sdna.captured"] is True


@pytest.mark.asyncio
async def test_ordinary_calls_are_left_alone():
    attrs = await check("payments", capture=False)
    assert "sdna.request.body" not in attrs


@pytest.mark.asyncio
async def test_capture_on_error_records_failed_calls_only(monkeypatch):
    monkeypatch.setenv("SERVICEDNA_CAPTURE_ON_ERROR", "true")
    assert "sdna.request.body" not in await check("payments", capture=False)
    exporter.clear()
    attrs = await check("broken", capture=False)
    assert json.loads(attrs["sdna.request.body"]) == {"service": "broken"}
    assert attrs["sdna.captured_on_error"] is True


def test_install_adds_the_interceptor_to_new_servers():
    import grpc.aio

    assert grpc_capture.install()
    assert getattr(grpc.aio.server, "_servicedna", False)
    assert grpc_capture.install()  # idempotent
