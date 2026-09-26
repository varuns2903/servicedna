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

## Test runs: capturing each hop's data

When ServiceDNA's Test Studio sends a request, it adds the baggage entry `sdna.capture=1`. For
those requests only, each SDK records the body the service received and the body it sent back on
the server span (`sdna.request.body`, `sdna.response.body`), so the run shows every hop's input
and output. Fields named like credentials (password, token, secret, API key, cookie, session,
card…) are masked, and bodies are capped at `SERVICEDNA_CAPTURE_MAX_BYTES` (16 KiB). Ordinary
traffic is never captured.

Values computed inside a function can be added to the run too — outside a test run it's a no-op:

| Language | |
|---|---|
| Node.js | `require('@servicedna/node').capture('order.total', total)` |
| Python | `servicedna.capture("order.total", total)` |
| Go | `servicedna.Capture(ctx, "order.total", total)` |
| Spring Boot | `ServiceDna.capture("order.total", total)` |

Body capture covers HTTP servers (and HTTP clients and Kafka producers/consumers in Node.js);
FastAPI/Starlette in Python and Spring MVC in Java. gRPC hops show up with status and timing but
not yet their messages.

Already using OpenTelemetry? No SDK is needed — point your exporter at ServiceDNA:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://servicedna.example.com/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_ik_...
OTEL_SERVICE_NAME=checkout
OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=prod
```
