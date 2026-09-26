from servicedna import config


def test_disabled_without_url_and_key():
    assert not config.resolve({}).enabled
    assert not config.resolve({"SERVICEDNA_URL": "http://x"}).enabled


def test_two_variables_are_enough():
    cfg = config.resolve({"SERVICEDNA_URL": "http://sdna:8080/", "SERVICEDNA_KEY": "sdna_ik_x", "OTEL_SERVICE_NAME": "payments"})
    assert cfg.enabled
    assert cfg.url == "http://sdna:8080"
    assert cfg.service_name == "payments"
    assert cfg.health_url is None


def test_heartbeat_checks_health_on_port():
    assert config.resolve({"PORT": "4005"}).local_health_url == "http://127.0.0.1:4005/health"
    assert config.resolve({"PORT": "8000", "SERVICEDNA_HEALTH_PATH": "/healthz"}).local_health_url == "http://127.0.0.1:8000/healthz"


def test_otel_environment_points_exporter_at_servicedna():
    cfg = config.resolve(
        {
            "SERVICEDNA_URL": "http://sdna:8080",
            "SERVICEDNA_KEY": "sdna_ik_x",
            "OTEL_SERVICE_NAME": "payments",
            "SERVICEDNA_ENV": "prod",
            "SERVICEDNA_HEALTH_URL": "http://payments:4005/health",
            "PORT": "4005",
        }
    )
    env = config.otel_environment(cfg)
    assert env["OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"] == "http://sdna:8080/api/v1/otlp/v1/traces"
    assert env["OTEL_EXPORTER_OTLP_TRACES_HEADERS"] == "x-servicedna-key=sdna_ik_x"
    assert env["OTEL_EXPORTER_OTLP_TRACES_PROTOCOL"] == "http/protobuf"
    assert "deployment.environment.name=prod" in env["OTEL_RESOURCE_ATTRIBUTES"]
    assert "servicedna.health.url=http://payments:4005/health" in env["OTEL_RESOURCE_ATTRIBUTES"]
    assert "telemetry.sdk.language=python" in env["OTEL_RESOURCE_ATTRIBUTES"]
    assert env["OTEL_PYTHON_EXCLUDED_URLS"] == "/health"


def test_logs_go_to_servicedna_unless_turned_off():
    base = {"SERVICEDNA_URL": "http://sdna:8080", "SERVICEDNA_KEY": "sdna_ik_x"}
    env = config.otel_environment(config.resolve(base))
    assert env["OTEL_LOGS_EXPORTER"] == "otlp"
    assert env["OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED"] == "true"
    assert env["OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"] == "http://sdna:8080/api/v1/otlp/v1/logs"
    assert env["OTEL_EXPORTER_OTLP_LOGS_HEADERS"] == "x-servicedna-key=sdna_ik_x"
    off = config.otel_environment(config.resolve({**base, "SERVICEDNA_LOGS": "false"}))
    assert off["OTEL_LOGS_EXPORTER"] == "none"
    assert "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT" not in off


def test_uvicorn_logs_reach_the_opentelemetry_handler():
    import logging

    import pytest

    uvicorn_config = pytest.importorskip("uvicorn.config")
    from servicedna import distro

    class LoggingHandler(logging.Handler):  # stands in for OpenTelemetry's
        def __init__(self):
            super().__init__()
            self.records = []

        def emit(self, record):
            self.records.append(record)

    handler = LoggingHandler()
    logging.getLogger().addHandler(handler)
    original = uvicorn_config.Config.configure_logging
    try:
        distro._bridge_uvicorn_logs()
        uvicorn_config.Config(app="x:y").configure_logging()
        logging.getLogger("uvicorn.error").error("Exception in ASGI application")
        logging.getLogger("uvicorn.access").info("GET /health 200")
        assert [r.getMessage() for r in handler.records] == ["Exception in ASGI application"]
    finally:
        uvicorn_config.Config.configure_logging = original
        logging.getLogger().removeHandler(handler)
        logging.getLogger("uvicorn").removeHandler(handler)
