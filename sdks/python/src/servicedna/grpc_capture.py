"""gRPC message capture: the request and response messages of unary calls a service handles,
recorded on its server span as JSON (masked and truncated like HTTP bodies) — for ServiceDNA test
runs (baggage ``sdna.capture=1``), and for failed calls with ``SERVICEDNA_CAPTURE_ON_ERROR=true``.

Installed by the distro: servers made with ``grpc.server`` / ``grpc.aio.server`` get the
interceptor after OpenTelemetry's, so the server span and the caller's baggage are current."""

from __future__ import annotations

import functools

from opentelemetry import trace

from .bodies import capture_on_error, capture_requested, redact


def _json(message) -> str:
    try:
        from google.protobuf.json_format import MessageToJson

        return MessageToJson(message, preserving_proto_field_name=True, indent=None)
    except Exception:  # not a protobuf message
        return str(message)


def _record(span, request, response, requested: bool) -> None:
    if not span.is_recording():
        return
    span.set_attribute("sdna.request.body", redact(_json(request)))
    if response is not None:
        span.set_attribute("sdna.response.body", redact(_json(response)))
    span.set_attribute("sdna.captured" if requested else "sdna.captured_on_error", True)


def _wrap_handler(handler, aio: bool):
    import grpc

    if handler is None or handler.unary_unary is None:
        return handler  # streaming calls aren't captured
    behavior = handler.unary_unary

    if aio:

        async def unary_unary(request, context):
            requested = capture_requested()
            if not requested and not capture_on_error():
                return await behavior(request, context)
            span = trace.get_current_span()
            try:
                response = await behavior(request, context)
            except BaseException:
                _record(span, request, None, requested)
                raise
            if requested:
                _record(span, request, response, True)
            return response

    else:

        def unary_unary(request, context):
            requested = capture_requested()
            if not requested and not capture_on_error():
                return behavior(request, context)
            span = trace.get_current_span()
            try:
                response = behavior(request, context)
            except BaseException:
                _record(span, request, None, requested)
                raise
            if requested:
                _record(span, request, response, True)
            return response

    return grpc.unary_unary_rpc_method_handler(
        unary_unary, request_deserializer=handler.request_deserializer, response_serializer=handler.response_serializer
    )


def server_interceptor():
    import grpc

    class CaptureInterceptor(grpc.ServerInterceptor):
        def intercept_service(self, continuation, handler_call_details):
            return _wrap_handler(continuation(handler_call_details), aio=False)

    return CaptureInterceptor()


def aio_server_interceptor():
    import grpc.aio

    class AioCaptureInterceptor(grpc.aio.ServerInterceptor):
        async def intercept_service(self, continuation, handler_call_details):
            return _wrap_handler(await continuation(handler_call_details), aio=True)

    return AioCaptureInterceptor()


def install() -> bool:
    """Adds the capture interceptor to every gRPC server made from now on. False without grpcio."""
    try:
        import grpc
        import grpc.aio
    except ImportError:
        return False
    if getattr(grpc.server, "_servicedna", False):
        return True

    def patch(module, name, make):
        original = getattr(module, name)

        @functools.wraps(original)
        def server(*args, **kwargs):
            interceptors = list(kwargs.pop("interceptors", None) or [])
            # grpc.server(thread_pool, handlers, interceptors, ...) also takes them positionally.
            if module is grpc and len(args) >= 3:
                interceptors = list(args[2] or []) + interceptors
                args = args[:2] + args[3:]
            interceptors.append(make())
            return original(*args, interceptors=interceptors, **kwargs)

        server._servicedna = True
        setattr(module, name, server)

    patch(grpc, "server", server_interceptor)
    patch(grpc.aio, "server", aio_server_interceptor)
    return True
