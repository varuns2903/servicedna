# ServiceDNA SDKs

Each SDK wraps OpenTelemetry with ServiceDNA defaults — nothing proprietary on the wire — and
needs only `SERVICEDNA_URL` and `SERVICEDNA_KEY` (an organization ingestion key). A service then
registers itself, sends traces, and reports health heartbeats.

| Language | Package | Status |
|---|---|---|
| Node.js | [`@servicedna/node`](node/) | ✅ |
| Python | [`servicedna`](python/) | ✅ |
| Go | [`github.com/varuns2903/servicedna/sdks/go`](go/) | ✅ |
| Java / Spring Boot | [`io.github.varuns2903:servicedna-spring-boot-starter`](java/) | ✅ |

## Following business keys

Tag the ids your services handle, and ServiceDNA can follow one through every service and log that
touched it — even where trace context was lost (a queue, a batch job, a worker picking work up
later):

| Language | |
|---|---|
| Node.js | `require('@servicedna/node').tag('orderId', order.id)` |
| Python | `servicedna.tag("orderId", order_id)` |
| Go | `servicedna.Tag(ctx, "orderId", order.ID)` |
| Spring Boot | `ServiceDna.tag("orderId", order.getId())` |

Tags go on the current span for all traffic (not only test runs), so tag ids, not personal data.
Then open **Follow a key** (or click a tagged value in a trace).

## Logs

Each SDK sends the service's logs to ServiceDNA next to its traces, tagged with the trace and span
of the request that wrote them — so a trace shows its logs, and a log line links to its trace. Your
output doesn't change.

| Language | What's sent |
|---|---|
| Node.js | `console.*`, pino, bunyan (`SERVICEDNA_LOGS_CONSOLE=false` leaves console out) |
| Python | the `logging` module, including uvicorn's startup and error logs (not access lines) |
| Go | `log/slog`'s default logger (use `slog.InfoContext(ctx, …)` for the trace link) and the `log` package |
| Spring Boot | Logback / Log4j |

`SERVICEDNA_LOGS=false` (Spring Boot: `servicedna.logs=false`) turns log export off.

## Test runs: capturing each hop's data

When ServiceDNA's Test Studio sends a request, it adds the baggage entry `sdna.capture=1`. For
those requests only, each SDK records the body the service received and the body it sent back on
the server span (`sdna.request.body`, `sdna.response.body`), so the run shows every hop's input
and output. Fields named like credentials (password, token, secret, API key, cookie, session,
card…) are masked, and bodies are capped at `SERVICEDNA_CAPTURE_MAX_BYTES` (16 KiB). Ordinary
traffic is never captured — unless you turn on capture on error.

### Capture on error (production)

Set `SERVICEDNA_CAPTURE_ON_ERROR=true` (Spring Boot: `servicedna.capture-on-error=true`) and every
request's bodies are held in memory until it finishes, then recorded **only if it failed** — a 5xx
or an unhandled exception (in Node.js, any span that ends in error, including a Kafka message whose
handler threw). Successful traffic ships no payloads; each failure arrives with what caused it,
masked the same way and marked `sdna.captured_on_error`. It's off by default: turn it on where
sending masked request data to ServiceDNA is acceptable.

Values computed inside a function can be added to the run too — outside a test run it's a no-op:

| Language | |
|---|---|
| Node.js | `require('@servicedna/node').capture('order.total', total)` |
| Python | `servicedna.capture("order.total", total)` |
| Go | `servicedna.Capture(ctx, "order.total", total)` |
| Spring Boot | `ServiceDna.capture("order.total", total)` |

Body capture covers HTTP servers (and HTTP clients and Kafka producers/consumers in Node.js);
FastAPI/Starlette and gRPC servers (unary calls, as JSON) in Python, and Spring MVC in Java. gRPC
servers in the other languages show up with status and timing, not yet their messages.

Already using OpenTelemetry? No SDK is needed — point your exporter at ServiceDNA:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://servicedna.example.com/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_ik_...
OTEL_SERVICE_NAME=checkout
OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=prod
```

## Releasing

Bump the version in the SDK's manifest, merge, then push a tag — the
[SDK release workflow](../.github/workflows/sdk-release.yml) tests and publishes it:

| SDK | Version in | Tag | Published to |
|---|---|---|---|
| Node.js | `node/package.json` | `node-v0.1.0` | npm `@servicedna/node` |
| Python | `python/pyproject.toml` | `python-v0.1.0` | PyPI `servicedna` |
| Spring Boot | `java/servicedna-spring-boot-starter/pom.xml` | `java-v0.1.0` | Maven Central `io.github.varuns2903:servicedna-spring-boot-starter` |
| Go | — (the tag is the version) | `sdks/go/v0.1.0` | the Go module proxy |

The tag must match the version, or the release stops before publishing.
