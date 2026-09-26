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
