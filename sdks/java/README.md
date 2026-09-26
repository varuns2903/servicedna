# ServiceDNA Spring Boot Starter

Connect a Spring Boot 3 service to [ServiceDNA](https://github.com/varuns2903/servicedna) the way
you'd add `spring-kafka`: one dependency, two settings. You get:

- **Distributed traces** for Spring MVC/WebFlux, RestTemplate/RestClient/WebClient, JDBC, Kafka,
  MongoDB, R2DBC and more (built on OpenTelemetry's Spring Boot starter — no Java agent)
- **Self-registration** — the service appears in ServiceDNA on first start, named after
  `spring.application.name`
- **Health heartbeats** — the actuator health endpoint (or `/health`) is checked and reported every
  15 s; health checks themselves aren't traced

## Add it

```xml
<dependency>
  <groupId>io.github.varuns2903</groupId>
  <artifactId>servicedna-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Spring Boot manages an older OpenTelemetry API than the starter needs, so also add:

```xml
<properties>
  <opentelemetry.version>1.65.0</opentelemetry.version>
</properties>
```

(Gradle: `ext['opentelemetry.version'] = '1.65.0'`.) If it's missing, startup fails with a message
saying exactly this.

## Configure

```yaml
servicedna:
  url: https://servicedna.example.com
  key: ${SERVICEDNA_KEY}
```

or just set `SERVICEDNA_URL` and `SERVICEDNA_KEY` as environment variables. Without them, tracing
is disabled and the application runs normally.

| Property / env var | Default | |
|---|---|---|
| `servicedna.url` / `SERVICEDNA_URL` | — | ServiceDNA base URL (required) |
| `servicedna.key` / `SERVICEDNA_KEY` | — | Organization ingestion key, from Settings → Integrations (required) |
| `servicedna.env` / `SERVICEDNA_ENV` | — | Environment (`dev`, `staging`, `prod`…); each is tracked separately |
| `servicedna.service` | `spring.application.name` | Service name |
| `servicedna.version` | — | |
| `servicedna.health-url` | — | Health URL **ServiceDNA** can reach, so it can also probe the service itself |
| `servicedna.health-path` | `/actuator/health` with actuator, else `/health` | Path the heartbeat checks locally |
| `servicedna.heartbeat-ms` | `15000` | `0` disables heartbeats |
| `servicedna.degraded-ms` | `2000` | Health response slower than this is reported DEGRADED |

Any `otel.*` property you set explicitly wins over the starter's defaults.

A runnable example is in [`example/`](example/).
