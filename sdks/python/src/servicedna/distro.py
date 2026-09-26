"""OpenTelemetry distro and configurator entry points, selected by OTEL_PYTHON_DISTRO /
OTEL_PYTHON_CONFIGURATOR=servicedna (set by servicedna-run and servicedna.start)."""

from __future__ import annotations

import logging
import os

from opentelemetry.instrumentation.distro import BaseDistro
from opentelemetry.sdk._configuration import _OTelSDKConfigurator

from . import bodies as _bodies
from . import grpc_capture as _grpc_capture
from . import config as _config
from . import heartbeat


class ServiceDnaDistro(BaseDistro):
    def _configure(self, **kwargs):
        cfg = _config.resolve()
        if not cfg.enabled:
            return
        for key, value in _config.otel_environment(cfg).items():
            os.environ.setdefault(key, value)
        # FastAPI is instrumented here, with body-capture hooks, instead of by auto-instrumentation.
        if _bodies.instrument_fastapi():
            disabled = [d for d in os.environ.get("OTEL_PYTHON_DISABLED_INSTRUMENTATIONS", "").split(",") if d]
            os.environ["OTEL_PYTHON_DISABLED_INSTRUMENTATIONS"] = ",".join(disabled + ["fastapi"])
        # gRPC request/response messages, for test runs (and failures with capture on error).
        _grpc_capture.install()
        if cfg.logs:
            _bridge_uvicorn_logs()
        heartbeat.start(cfg)


def _bridge_uvicorn_logs() -> None:
    """uvicorn's loggers don't propagate to the root logger, where OpenTelemetry's handler sits.
    Once uvicorn has configured logging, its "uvicorn" logger (startup, errors and tracebacks —
    not per-request access lines, which spans already cover) gets that handler too."""
    try:
        import uvicorn.config
    except ImportError:
        return
    original = uvicorn.config.Config.configure_logging
    if getattr(original, "_servicedna", False):
        return

    def configure_logging(self):
        original(self)
        handlers = [h for h in logging.getLogger().handlers if type(h).__name__ == "LoggingHandler"]
        target = logging.getLogger("uvicorn")
        for handler in handlers:
            if handler not in target.handlers:
                target.addHandler(handler)

    configure_logging._servicedna = True
    uvicorn.config.Config.configure_logging = configure_logging


class ServiceDnaConfigurator(_OTelSDKConfigurator):
    """The standard OpenTelemetry SDK setup (tracer provider, batch span processor, exporter)."""
