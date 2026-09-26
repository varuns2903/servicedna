# ServiceDNA Go SDK

Connect a Go service to [ServiceDNA](https://github.com/varuns2903/servicedna): traces,
self-registration and health heartbeats, configured with two environment variables.

```bash
go get github.com/varuns2903/servicedna/sdks/go
```

```go
import (
	servicedna "github.com/varuns2903/servicedna/sdks/go"
	"github.com/varuns2903/servicedna/sdks/go/sdnahttp"
)

func main() {
	shutdown, err := servicedna.Start(context.Background()) // reads SERVICEDNA_* env vars
	if err != nil {
		log.Fatal(err)
	}
	defer shutdown(context.Background())

	mux := http.NewServeMux()
	mux.HandleFunc("GET /orders/{id}", getOrder)

	// Outgoing calls: use sdnahttp.Client() or sdnahttp.Transport(...) so trace context
	// propagates to the services you call.
	log.Fatal(http.ListenAndServe(":8080", sdnahttp.Handler(mux)))
}
```

```bash
SERVICEDNA_URL=https://servicedna.example.com SERVICEDNA_KEY=sdna_ik_... ./my-service
```

Go has no runtime auto-instrumentation, so the two wrappers above are the only code changes. Spans
are named by `ServeMux` pattern (`GET /orders/{id}`); health-check requests aren't traced. Without
`SERVICEDNA_URL` and `SERVICEDNA_KEY`, `Start` does nothing and the wrappers are pass-through.

For zero code changes, eBPF instrumentation (Grafana Beyla, OpenTelemetry Go Auto-Instrumentation)
can export OTLP to ServiceDNA instead.

## Configuration

| Variable | Default | |
|---|---|---|
| `SERVICEDNA_URL` | — | ServiceDNA base URL (required) |
| `SERVICEDNA_KEY` | — | Organization ingestion key, from Settings → Integrations (required) |
| `SERVICEDNA_ENV` | — | Environment (`dev`, `staging`, `prod`…); each is tracked separately |
| `OTEL_SERVICE_NAME` / `SERVICEDNA_SERVICE` | executable name | Service name |
| `SERVICEDNA_VERSION` | — | |
| `SERVICEDNA_HEALTH_URL` | — | Health URL **ServiceDNA** can reach, so it can also probe the service itself |
| `SERVICEDNA_HEALTH_PATH` | `/health` | Path the heartbeat checks on `http://127.0.0.1:$PORT` |
| `SERVICEDNA_LOCAL_HEALTH_URL` | from `PORT` + path | Full URL the heartbeat checks, if not on `PORT` |
| `SERVICEDNA_HEARTBEAT_MS` | `15000` | `0` disables heartbeats |
| `SERVICEDNA_DEGRADED_MS` | `2000` | Health response slower than this is reported DEGRADED |

Requires Go 1.25+ (current OpenTelemetry Go).
