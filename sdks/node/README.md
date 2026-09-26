# @servicedna/node

Connect a Node.js service to [ServiceDNA](https://github.com/varuns2903/servicedna) with one
dependency and two environment variables. You get:

- **Distributed traces** for HTTP, Express/Fastify/Koa, gRPC, GraphQL, Kafka, databases and more
  (OpenTelemetry auto-instrumentation), stored per organization in ServiceDNA
- **Self-registration** — the service appears in ServiceDNA on first start, with its environment,
  language and version; no manual registration
- **Health heartbeats** — the service's own `/health` is checked and reported every 15 s

## Install

```bash
npm install @servicedna/node
```

## Run

```bash
SERVICEDNA_URL=https://servicedna.example.com \
SERVICEDNA_KEY=sdna_ik_... \
node -r @servicedna/node/register src/index.js
```

Or start it from code, before anything else is required:

```js
require('@servicedna/node').start();
const express = require('express'); // instrumented
```

Without `SERVICEDNA_URL` and `SERVICEDNA_KEY` the SDK logs a warning and does nothing, so the same
build runs anywhere.

## Configuration

| Variable | Default | |
|---|---|---|
| `SERVICEDNA_URL` | — | ServiceDNA base URL (required) |
| `SERVICEDNA_KEY` | — | Organization ingestion key, from Settings → Integrations (required) |
| `SERVICEDNA_ENV` | `NODE_ENV` | Environment (`dev`, `staging`, `prod`…); each is tracked separately |
| `OTEL_SERVICE_NAME` / `SERVICEDNA_SERVICE` | `package.json` name | Service name |
| `SERVICEDNA_VERSION` | `package.json` version | |
| `SERVICEDNA_HEALTH_URL` | — | Health URL **ServiceDNA** can reach, so it can also probe the service itself |
| `SERVICEDNA_HEALTH_PATH` | `/health` | Path the heartbeat checks on `http://127.0.0.1:$PORT` |
| `SERVICEDNA_LOCAL_HEALTH_URL` | from `PORT` + path | Full URL the heartbeat checks, if not on `PORT` |
| `SERVICEDNA_HEARTBEAT_MS` | `15000` | `0` disables heartbeats |
| `SERVICEDNA_DEGRADED_MS` | `2000` | Health response slower than this is reported DEGRADED |

Standard `OTEL_*` variables (sampling, propagators, disabling instrumentations) work as usual.
Health-check requests and file-system calls aren't traced.

## Notes

- CommonJS applications are fully instrumented with `-r`. Native ES-module applications need
  OpenTelemetry's loader hooks for auto-instrumentation to apply to `import`ed libraries.
- `start()` returns `{ shutdown }`; the SDK also flushes on `SIGTERM`/`SIGINT`.
