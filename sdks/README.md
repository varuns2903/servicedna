# ServiceDNA SDKs

Each SDK wraps OpenTelemetry with ServiceDNA defaults — nothing proprietary on the wire — and
needs only `SERVICEDNA_URL` and `SERVICEDNA_KEY` (an organization ingestion key). A service then
registers itself, sends traces, and reports health heartbeats.

| Language | Package | Status |
|---|---|---|
| Node.js | [`@servicedna/node`](node/) | ✅ |
| Python | [`servicedna`](python/) | ✅ |

Already using OpenTelemetry? No SDK is needed — point your exporter at ServiceDNA:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://servicedna.example.com/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_ik_...
OTEL_SERVICE_NAME=checkout
OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=prod
```
