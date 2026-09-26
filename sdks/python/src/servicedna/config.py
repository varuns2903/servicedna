"""Resolves SDK settings from environment variables. Only SERVICEDNA_URL and SERVICEDNA_KEY are
required; everything else has a default."""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Mapping, Optional


@dataclass(frozen=True)
class Config:
    url: str
    key: str
    service_name: str
    environment: Optional[str]
    version: Optional[str]
    health_url: Optional[str]  # what ServiceDNA probes; only reported when set
    local_health_url: Optional[str]  # what the heartbeat checks, over loopback
    heartbeat_seconds: float
    degraded_ms: int
    logs: bool = True  # SERVICEDNA_LOGS=false stops sending the logging module's records

    @property
    def enabled(self) -> bool:
        return bool(self.url and self.key)


def resolve(env: Mapping[str, str] = os.environ) -> Config:
    port = env.get("PORT")
    health_path = env.get("SERVICEDNA_HEALTH_PATH", "/health")
    return Config(
        url=env.get("SERVICEDNA_URL", "").rstrip("/"),
        key=env.get("SERVICEDNA_KEY", ""),
        service_name=env.get("OTEL_SERVICE_NAME") or env.get("SERVICEDNA_SERVICE") or _default_service_name(),
        environment=env.get("SERVICEDNA_ENV") or None,
        version=env.get("SERVICEDNA_VERSION") or None,
        health_url=env.get("SERVICEDNA_HEALTH_URL") or None,
        local_health_url=env.get("SERVICEDNA_LOCAL_HEALTH_URL")
        or (f"http://127.0.0.1:{port}{health_path}" if port else None),
        heartbeat_seconds=int(env.get("SERVICEDNA_HEARTBEAT_MS", "15000")) / 1000,
        degraded_ms=int(env.get("SERVICEDNA_DEGRADED_MS", "2000")),
        logs=env.get("SERVICEDNA_LOGS", "true").lower() not in ("0", "false", "no", "off"),
    )


def _default_service_name() -> str:
    return os.path.basename(os.getcwd()) or "unknown_service:python"


def otel_environment(config: Config) -> dict:
    """The OTEL_* settings that send this service's traces and logs to ServiceDNA. Values the user already
    set are respected by the caller (setdefault), so standard OpenTelemetry configuration still
    works."""
    attributes = [f"telemetry.sdk.language=python"]
    if config.environment:
        attributes.append(f"deployment.environment.name={config.environment}")
    if config.version:
        attributes.append(f"service.version={config.version}")
    if config.health_url:
        attributes.append(f"servicedna.health.url={config.health_url}")
    excluded = [config.local_health_url.split("://", 1)[1].split("/", 1)[1]] if config.local_health_url else []
    logs = (
        {
            # Records from the logging module, with the active trace and span ids.
            "OTEL_LOGS_EXPORTER": "otlp",
            "OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED": "true",
            "OTEL_EXPORTER_OTLP_LOGS_PROTOCOL": "http/protobuf",
            "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT": f"{config.url}/api/v1/otlp/v1/logs",
            "OTEL_EXPORTER_OTLP_LOGS_HEADERS": f"x-servicedna-key={config.key}",
        }
        if config.logs
        else {"OTEL_LOGS_EXPORTER": "none"}
    )
    return {
        "OTEL_SERVICE_NAME": config.service_name,
        "OTEL_RESOURCE_ATTRIBUTES": ",".join(attributes),
        "OTEL_TRACES_EXPORTER": "otlp",
        "OTEL_METRICS_EXPORTER": "none",
        **logs,
        "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL": "http/protobuf",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT": f"{config.url}/api/v1/otlp/v1/traces",
        "OTEL_EXPORTER_OTLP_TRACES_HEADERS": f"x-servicedna-key={config.key}",
        # Health probes (ServiceDNA's and the heartbeat's) aren't traffic worth tracing.
        "OTEL_PYTHON_EXCLUDED_URLS": ",".join(f"/{path}" for path in excluded),
        "OTEL_PYTHON_DISTRO": "servicedna",
        "OTEL_PYTHON_CONFIGURATOR": "servicedna",
    }
