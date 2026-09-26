"""OpenTelemetry distro and configurator entry points, selected by OTEL_PYTHON_DISTRO /
OTEL_PYTHON_CONFIGURATOR=servicedna (set by servicedna-run and servicedna.start)."""

from __future__ import annotations

import os

from opentelemetry.instrumentation.distro import BaseDistro
from opentelemetry.sdk._configuration import _OTelSDKConfigurator

from . import bodies as _bodies
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
        heartbeat.start(cfg)


class ServiceDnaConfigurator(_OTelSDKConfigurator):
    """The standard OpenTelemetry SDK setup (tracer provider, batch span processor, exporter)."""
