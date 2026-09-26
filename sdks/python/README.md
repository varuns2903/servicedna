# servicedna (Python)

Connect a Python service to [ServiceDNA](https://github.com/varuns2903/servicedna) with one
dependency and two environment variables. You get:

- **Distributed traces** for FastAPI, Flask, Django, ASGI/WSGI, requests, httpx, urllib3,
  aiohttp, gRPC, SQLAlchemy, psycopg2, Redis and Kafka (OpenTelemetry auto-instrumentation —
  only libraries you actually use are instrumented)
- **Self-registration** — the service appears in ServiceDNA on first start
- **Health heartbeats** — the service's own `/health` is checked and reported every 15 s

## Install

```bash
pip install servicedna
```

## Run

Prefix your usual command with `servicedna-run`:

```bash
SERVICEDNA_URL=https://servicedna.example.com \
SERVICEDNA_KEY=sdna_ik_... \
servicedna-run uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Or start it from code, first thing in your entry point:

```python
import servicedna
servicedna.start()
```

`servicedna-run` is preferred: it instruments libraries before your code imports them. Without
`SERVICEDNA_URL` and `SERVICEDNA_KEY` both do nothing, so the same build runs anywhere.

## Configuration

| Variable | Default | |
|---|---|---|
| `SERVICEDNA_URL` | — | ServiceDNA base URL (required) |
| `SERVICEDNA_KEY` | — | Organization ingestion key, from Settings → Integrations (required) |
| `SERVICEDNA_ENV` | — | Environment (`dev`, `staging`, `prod`…); each is tracked separately |
| `OTEL_SERVICE_NAME` / `SERVICEDNA_SERVICE` | working directory name | Service name |
| `SERVICEDNA_VERSION` | — | |
| `SERVICEDNA_HEALTH_URL` | — | Health URL **ServiceDNA** can reach, so it can also probe the service itself |
| `SERVICEDNA_HEALTH_PATH` | `/health` | Path the heartbeat checks on `http://127.0.0.1:$PORT` |
| `SERVICEDNA_LOCAL_HEALTH_URL` | from `PORT` + path | Full URL the heartbeat checks, if not on `PORT` |
| `SERVICEDNA_HEARTBEAT_MS` | `15000` | `0` disables heartbeats |
| `SERVICEDNA_DEGRADED_MS` | `2000` | Health response slower than this is reported DEGRADED |

Standard `OTEL_*` variables still apply and win over these defaults.
