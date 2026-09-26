"""OpenTelemetry distro and configurator entry points, selected by OTEL_PYTHON_DISTRO /
OTEL_PYTHON_CONFIGURATOR=servicedna (set by servicedna-run and servicedna.start)."""

from __future__ import annotations

import os

from opentelemetry.instrumentation.distro import BaseDistro
from opentelemetry.sdk._configuration import _OTelSDKConfigurator

from . import config as _config
from . import heartbeat


class ServiceDnaDistro(BaseDistro):
    def _configure(self, **kwargs):
        cfg = _config.resolve()
        if not cfg.enabled:
            return
        for key, value in _config.otel_environment(cfg).items():
            os.environ.setdefault(key, value)
        heartbeat.start(cfg)


class ServiceDnaConfigurator(_OTelSDKConfigurator):
    """The standard OpenTelemetry SDK setup (tracer provider, batch span processor, exporter)."""
