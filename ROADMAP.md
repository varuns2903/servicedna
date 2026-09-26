# ServiceDNA Roadmap

This roadmap collects every bug, gap, and feature identified while testing ServiceDNA against
[ShopLite](https://github.com/servicedna-playground), a 7-service polyglot demo system (Node,
Python, Go).

The goal: **connecting ServiceDNA to a service should feel like adding a logger or a Kafka client.**
Add one dependency, set two environment variables, start the app — and the service shows up in
ServiceDNA with its health, dependencies, API flows, and traces. No manual registration, no
hand-drawn graphs.

---

## Guiding principles

| Principle | What it means in practice | Familiar equivalent |
|---|---|---|
| **One dependency, zero code** | `servicedna-spring-boot-starter`, `@servicedna/node`, `servicedna` (pip), `sdk-go` auto-configure everything | `spring-kafka`, `logback` + `slf4j` |
| **Configure with env vars** | `SERVICEDNA_URL`, `SERVICEDNA_KEY`, `SERVICEDNA_ENV` — sensible defaults for everything else | `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT` |
| **Services register themselves** | First start = service appears. Name comes from `spring.application.name` / `package.json` / `pyproject.toml` | Kafka topics auto-created on first produce |
| **Discover, don't declare** | Dependencies and API flows come from real traffic; manual entry becomes optional | Kibana index patterns picked up from incoming data |
| **Open standards in, no lock-in** | Accept OTLP and Zipkin as-is. Teams already on OpenTelemetry only change an exporter URL | Logstash accepting any Beats/Syslog input |
| **Try it from the UI** | Pick a topic/endpoint, get a payload template, send it, watch it flow | Kafka UI "Produce message", Postman |
| **Search like a log tool** | Find any request by service, status, time, or business key (`orderId=o-17`) | Kibana / OpenSearch Discover |
| **Safe by default** | Payload capture off unless requested; redaction runs *inside* the customer network; outbound-only connections | Log masking appenders |

**Integration success criteria** (tracked for every SDK):

- ≤ 5 minutes from sign-up to first service visible
- ≤ 2 lines of code (0 for Java/Node/Python auto-instrumentation)
- No inbound firewall rules needed in the customer environment
- Works identically in dev, stage, and prod — only `SERVICEDNA_ENV` changes

---

## Phase 0 — Fix what's broken

Bugs found while running ShopLite. Small, independent, do these first.

| # | Issue | Where | Status |
|---|---|---|---|
| 0.1 | Alert-rule list crashes the Services detail and Alerts pages (`Cannot read properties of undefined (reading 'replace')`) — frontend expected `condition` as an object and different enum values than the backend | `frontend/src/api/alerts.api.ts`, `AlertsList.tsx`, `ServiceDetails.tsx` | ✅ Done |
| 0.2 | The same mismatch made "New Rule" in the UI send an invalid payload | same | ✅ Done |
| 0.3 | Malformed request body returns `500` instead of `400` — JSON deserialization errors aren't mapped in the global exception handler | `common/` exception handler | ✅ Done — also covers missing headers, bad path IDs, 405s |
| 0.4 | Kafka healthcheck hardcodes port `9092`, so setting `KAFKA_PORT` leaves Kafka unhealthy and the backend never starts. Fix: check `29092` | `docker-compose.yml` | ✅ Done |
| 0.5 | Alert webhook message prints the service UUID twice instead of the service name | `AlertEventConsumer.triggerWebhook` | ✅ Done |
| 0.6 | Docs claim alert rules support latency / error-rate / consecutive-failure conditions and auto-open incidents; code supports only `STATUS_DOWN/DEGRADED/RECOVERED` and only fires webhooks | `README.md`, `API.md` | ✅ Docs fixed; features in Phase 1 |
| 0.7 | Possible duplicate alerts: two identical DEGRADED alerts seen for one service — suspected race when the active prober and a push ping flip status concurrently (no locking on the status read-modify-write) | `HealthCheckProberService`, `PingService` | ✅ Done — confirmed race; `ServiceStatusRecorder` applies changes under a row lock |
| 0.8 | Prober and push agent measure latency differently, so near the 2 s DEGRADED threshold a service flaps between states | same | ✅ Done — `HEALTH_CHECK_STATUS_CONFIRMATIONS` (default 2) |
| 0.9 | Status changes from pushed pings never evicted the services cache — UI showed stale status for up to 10 min | `PingService` | ✅ Done |
| 0.10 | Tests ran on H2 with the PostgreSQL dialect (`application.yml` overrode the test profile) | `application-test.yml` | ✅ Done |

---

## Phase 1 — Close the core gaps

### 1.1 Alerts open incidents automatically — ✅ Done
Today an outage at 3am fires a webhook and pages nobody, because on-call paging and escalation
only start when a human creates an incident.

- Alert rule option: **Open incident** with a severity (e.g. `STATUS_DOWN → CRITICAL`)
- Auto-resolve (or move to `MONITORING`) on `STATUS_RECOVERED`
- De-duplicate: one open incident per service per condition; repeat events append to its timeline
- ~~Affected services pre-filled from the dependency graph~~ → moved to **1.1b**

### 1.1b Group cascading failures into one incident — ✅ Done
Found while testing 1.1: when payment-service goes down, order-service (which depends on it) goes
down too and gets its own incident — two incidents for one root cause.
- When a service's dependency (declared, later observed — Phase 3) already has an open alert
  incident, add the service to that incident instead of opening a new one
- Resolve the grouped incident only once every affected service has recovered
- Timeline shows the cascade: "order-service DOWN — depends on payment-service"
- Incident retitled after the likeliest root cause when a deeper dependency joins
- Uses the declared graph today; switches to the observed graph once Phase 3 lands

### 1.2 Threshold alert conditions — ✅ Done
The UI originally promised these; implement them for real:
- `LATENCY_ABOVE` (ms, over N minutes), `ERROR_RATE_ABOVE` (%, over N minutes),
  `CONSECUTIVE_FAILURES` (count)
- Evaluated by a scheduled evaluator (every 30 s) against recent pings, not the Kafka consumer — thresholds can be crossed without any status change (later: against trace metrics)

### 1.3 Plan limits enforced — ✅ Done
Billing shows "Free: 3 services max", but nothing enforces it.
- One `PlanLimits` source of truth (Free 3 / Pro 50 / Enterprise unlimited)
- Enforced in service create **and** CSV import → `403 PLAN_LIMIT_REACHED`
- Orgs already over the limit keep existing services; new ones blocked
- Billing page shows usage ("7 / 3 services") with an upgrade prompt
- Per-plan ping retention (Free 1 day, Pro 30 days, Enterprise unlimited) — currently a global 90 days
- Replace the hardcoded fake "Manage Billing Portal" URL with a real Stripe portal session
- Dev convenience: a way to set an org's plan locally without Stripe (e.g. ShopLite `SDNA_PLAN=PRO`)

---

## Phase 2 — Effortless onboarding (the "slf4j moment")

This phase is the foundation for everything after it: once services emit telemetry through a
ServiceDNA SDK, the graph, flows, and testing features light up without extra setup.

### 2.1 OTLP ingestion in ServiceDNA — ✅ Done
- Accept **OTLP** (gRPC + HTTP) and **Zipkin** spans
- Authenticated with an **org ingestion key**; service identity comes from OTel resource
  attributes (`service.name`, `deployment.environment`)
- Existing per-service API keys and `POST /api/v1/ping` keep working
- Storage: full traces in **Tempo** (or Jaeger); only aggregates (edges, metrics) in Postgres
- Ships in `docker-compose.yml` so local setup stays one command

### 2.2 Language SDKs — auto-configured — ✅ Done
Each SDK wraps OpenTelemetry with ServiceDNA defaults; nothing proprietary on the wire.

**Java / Spring Boot** — like adding `spring-kafka`:
```xml
<dependency>
  <groupId>io.servicedna</groupId>
  <artifactId>servicedna-spring-boot-starter</artifactId>
</dependency>
```
```yaml
servicedna:
  url: https://servicedna.example.com
  key: ${SERVICEDNA_KEY}
```
Auto-detects the Actuator health endpoint, `spring.application.name`, and instruments
RestTemplate/WebClient, gRPC, Kafka, JDBC.

**Node.js:**
```bash
npm i @servicedna/node
node --import @servicedna/node/register src/index.js
```

**Python:**
```bash
pip install servicedna
servicedna-run uvicorn app.main:app
```

**Go** (manual wiring is idiomatic in Go; keep it to two calls):
```go
shutdown := servicedna.Start(ctx)           // reads SERVICEDNA_* env vars
defer shutdown()
http.ListenAndServe(":8080", sdnahttp.Handler(mux))
```

**Already on OpenTelemetry?** No SDK needed:
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://servicedna.example.com/otlp
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=<key>
```

**No code changes possible?** Supported fallbacks: service-mesh sidecar (Envoy/Istio) or eBPF
auto-instrumentation (Grafana Beyla / Odigos) pointed at the ServiceDNA collector.

### 2.3 Self-registration — ✅ Done
- A service's first signal registers it automatically: name, environment, language, version,
  host, and discovered health endpoint
- Manual registration remains for services that can't run an SDK
- Registered services are grouped by `deployment.environment` (dev / stage / prod)

### 2.4 ServiceDNA Agent (collector + runner) per environment — ✅ Collector done (runner: Phase 5)
One install per cluster/environment, like installing a Filebeat or a Kafka Connect worker:
```bash
helm install servicedna-agent servicedna/agent --set key=$SERVICEDNA_KEY --set env=prod
# or: docker compose -f servicedna-agent.yml up -d
```
- **Collector**: receives telemetry, applies redaction and sampling *inside* the customer
  network, forwards to ServiceDNA over TLS — outbound connections only
- **Runner**: pulls test jobs from ServiceDNA and executes them inside the network, like a GitHub
  self-hosted runner — see [5.2 The Runner](#52-the-runner--how-test-requests-reach-real-services)
- Optional **Kafka watcher**: joins topics as its own consumer group to capture events without
  instrumenting producers

### 2.5 Onboarding wizard in the UI — ✅ Done
Modelled on Sentry/Datadog onboarding:
1. Pick language/framework → copy the two lines shown
2. Screen waits live: *"Waiting for first signal from your service…"*
3. Service appears → wizard links straight to its graph and health

### 2.6 `sdna` CLI — ✅ Done (login, orgs, status, keys, init; `test run` / `trace` come with Phases 5–6)
```bash
sdna login
sdna init                 # detects framework, adds the SDK, writes servicedna.yaml
sdna status               # services, health, recent incidents
sdna test run flows/      # run saved flow tests (CI-friendly exit codes)
sdna trace <trace-id>     # print a hop-by-hop flow in the terminal
```

---

## Phase 3 — Automatic dependency graph

The dependency graph is a headline feature, but today every edge is typed by hand and never
validated.

### 3.1 Observed graph from traces — ✅ Done
- A client span in service A whose child server span is in service B ⇒ edge `A → B`
- Built with the OTel Collector `servicegraph` connector; aggregated into
  `observed_edges(from, to, protocol, calls, errors, p95, last_seen)`
- Kafka/async edges from span links (producer → topic → consumer)
- Databases and external APIs (Stripe, Twilio…) shown as distinct node types

### 3.2 Declared vs. observed (drift detection) — ✅ Done (alerting on new undeclared dependencies: later)
- Manual edges stay as the **declared** graph
- UI highlights: *declared but never seen* (stale docs) and *seen but undeclared* (hidden
  dependency)
- Optional alert when a new undeclared dependency appears

### 3.3 Static discovery from repositories — ✅ Done via `sdna scan` (GitHub App sync: Phase 7)
Gives `repositoryUrl` a real purpose:
- Scan repos for OpenAPI, `.proto`, AsyncAPI, GraphQL schemas, and service URLs in config / env
- Produces a graph **before any traffic exists** and an API catalog used by Phase 5

### 3.4 Other discovery sources
- Kubernetes / Docker labels and service names
- Service mesh telemetry (Istio/Linkerd) when present

---

## Phase 4 — API call flow graph

Same trace data, finer grain: nodes are **operations**, not services.

- Protocol-aware from OTel semantic conventions:
  HTTP (`http.route`), gRPC (`rpc.service`/`rpc.method`), GraphQL (`graphql.operation.name`),
  messaging (`messaging.system`/`messaging.destination`), databases (`db.system`)
- Edges show protocol, calls/min, error rate, p50/p95 latency
- Two views: **org-wide flow map** and **per-entry-point flowchart** (e.g. everything that
  happens behind `POST /api/orders`)
- Click any edge → recent traces through it

---

## Phase 5 — Data-flow testing ("Test Studio")

Send real data through the real code in dev / stage / prod and see each service's input and
output — the Kafka-UI-produce-message experience, extended across the whole system.

### 5.1 How it works
1. In **Test Studio**, pick an entry point from the discovered catalog: REST endpoint, gRPC
   method, GraphQL operation, or Kafka topic
2. ServiceDNA generates a **payload template** from the schema (OpenAPI / proto / AsyncAPI /
   recent real messages); edit and send
3. The **Runner** in that environment fires the request with trace context:
   ```
   traceparent: 00-<trace-id>-<span-id>-01
   baggage:     sdna.run=<run-id>,sdna.capture=1
   ```
4. Instrumentation propagates the context through every HTTP call, gRPC call, and Kafka message;
   the SDK sees `sdna.capture=1` and records each hop's **request/response bodies**, headers,
   status, and exceptions
5. ServiceDNA assembles the trace **live** into a hop-by-hop view; the failing hop is highlighted
   and hops that never ran are shown

Example (ShopLite, amount over the fraud limit):
```
✓ gateway            in: {userId:u-1, items:[...]}
✓ order → user       GET  /users/u-1          ← 200 {email:...}
✓ order → product    GET  /products/p-3       ← 200 {price:329.99}
✓ order → inventory  POST /reserve {qty:20}   ← 200 {remaining:20}
✗ order → payment    POST /charge {amount:6599.8} ← 402 "declined: amount exceeds 5000"
✗ order              responds 402   (notification never called)
```

### 5.2 The Runner — how test requests reach real services

ServiceDNA never executes service code, and its server usually can't reach private services or
brokers. A **Runner** inside the target network receives test jobs and makes the call with an
ordinary HTTP / gRPC / Kafka client — the service gets a completely normal request or message.

```
Developer        ServiceDNA server          Runner (inside the env)        Service / Kafka
   │ Test Studio:       │                          │                             │
   │ target + payload   │                          │                             │
   ├──── Send ─────────▶│ creates test job          │                             │
   │                    │ (run-id, target,          │                             │
   │                    │  payload, trace-id)       │                             │
   │                    │◀──── long-poll / WS ──────┤  (outbound connection only) │
   │                    ├──── job ─────────────────▶│                             │
   │                    │                          ├── HTTP / gRPC / produce ───▶│
   │                    │                          │   + traceparent, baggage    │
   │                    │◀── entry result ──────────┤  (HTTP response / Kafka ack)│
   │                    │◀──────────── spans + captured bodies from every hop (SDKs)
   │◀── live hop view ──┤                          │                             │
```

Results arrive by two paths: the **Runner** reports only the entry call's result (HTTP response,
gRPC reply, Kafka partition/offset ack); everything downstream is reported by each service's
**SDK** and joined by trace-id.

#### Deployment modes

| Setup | Runner |
|---|---|
| Self-hosted ServiceDNA on the same network (e.g. ShopLite today) | **Embedded** in the ServiceDNA backend — no extra install; it already has a Kafka client and network reach |
| Hosted ServiceDNA, private environments | Part of the **Agent** (`helm install servicedna-agent`); outbound connection only |
| Developer laptop / CI | **`sdna test run`** — the CLI acts as the Runner using the local network, like `kafka-console-producer` |

#### Configuration (once per environment)

Connection details and credentials live in the Agent config, inside the customer network — the
ServiceDNA server never stores broker passwords or internal URLs' secrets.

```yaml
runner:
  kafka:
    clusters:
      - name: main
        bootstrapServers: kafka:9092
        sasl: { mechanism: SCRAM-SHA-512, secretRef: kafka-creds }
        schemaRegistry: http://schema-registry:8081      # Avro / Protobuf encoding
  http:
    # Optional overrides; by default targets come from self-registered service addresses
    targets:
      api-gateway: https://gateway.stage.internal
  allow:
    environments: [dev, stage]        # prod requires explicit opt-in
    topics: ["order.*", "payment.*"]
    services: ["*"]
```

#### Target discovery

| Target | Address comes from | Operations / schema come from |
|---|---|---|
| REST | Service's self-registered address (e.g. K8s DNS `order-service.shop.svc:4004`) | Observed `http.route` spans, OpenAPI specs |
| gRPC | Same | `.proto` from repo scan, or server reflection |
| GraphQL | Same | Schema from repo or introspection |
| Kafka | Runner's configured cluster | Topics from SDK spans (`messaging.destination`) + broker AdminClient listing; payload schema from Schema Registry, AsyncAPI, or recent message samples |

Test Studio shows who is involved before sending — e.g. topic `order.created` → *consumed by
notification-service, analytics-service*. Any registered service can be an entry point, not just
the gateway (e.g. hit `payment-service POST /charge` to test one subgraph).

#### How each protocol is executed

- **REST / GraphQL**: plain HTTP request; `traceparent` + `baggage` headers added; response
  recorded as the entry result
- **gRPC**: JSON payload encoded to protobuf dynamically (like `grpcurl`); trace context in call
  metadata
- **Kafka**: a normal produce with key, value, and headers. The equivalent `kcat` command:
  ```bash
  kcat -P -b kafka:9092 -t order.created -k o-17 \
    -H "traceparent=00-4bf92f35...-00f067aa...-01" \
    -H "baggage=sdna.run=run_81,sdna.capture=1" \
    <<< '{"orderId":"o-17","userId":"u-1","total":59.0}'
  ```
  Avro/Protobuf values are serialized via the Schema Registry first. The consuming service's
  instrumentation extracts `traceparent` from the message headers and continues the same trace;
  `sdna.capture=1` makes its SDK record what it received and everything it does next.

#### Guardrails

- Per-environment permission; **prod disabled by default**
- Allow-lists for topics, services, and endpoints
- RBAC on who can run tests (reuses org roles) and every run recorded in the **audit log**
- `sdna.test` baggage flag services can honour to dry-run side effects
- Rate limits and payload size caps per run

### 5.3 Completion detection
- Sync flows: the entry response is the final result
- Async flows: done when the entry call has responded **and** no new spans arrive for N seconds,
  or all assertions pass, or a timeout expires
- Terminal consumers (e.g. `notification-service`) are shown as final outputs

### 5.4 Collections, assertions, CI
- Save requests as **collections** (like Postman), per environment
- Assertions on any hop: status, latency, body fields, "event published to `order.created`
  containing `orderId`"
- Flows stored as YAML in the repo and run with `sdna test run` in CI; results linked back to the
  commit

### 5.5 Capturing values inside a service
Function-internal values can't be observed from outside; one line exposes them:
```js
sdna.capture('order.total', total)
```
Log lines emitted during the request are attached to their hop automatically via trace-id
correlation (logback/log4j/winston/structlog bridges).

### 5.6 Safety
- Payload capture is **opt-in per request** (baggage flag) — never on for normal traffic
- **Redaction rules** (`password`, `card`, `ssn`, custom JSONPaths) enforced in the Agent
  before data leaves the network; size caps on bodies
- **Side effects**: services can honour a `sdna.test` flag to dry-run (skip real charges/emails);
  prod test runs require explicit per-environment permission

---

## Phase 6 — Bug analysis

### 6.1 Error-only payload capture in prod
The SDK buffers request/response bodies in memory for the duration of a request and attaches
them **only if the span ends in error**. Successful traffic ships no payloads; every failure
arrives with full context. Tail sampling keeps 100% of errors and test runs.

### 6.2 Trace & log explorer
Kibana-style search across traces and correlated logs: by service, operation, status, latency,
environment, time range, and attributes.

### 6.3 Business-key tracking
When trace context is broken (custom thread pools, batch jobs, uninstrumented hops), follow a
domain ID instead — `orderId=o-17`, `paymentId=…` — across spans, logs, and Kafka messages.

### 6.4 Incident integration
- Incident page shows failing traces from its time window and affected services
- Attach a trace to an incident; the post-mortem timeline includes the hop-by-hop failure
- **Replay** a captured failing request (in a non-prod environment) to reproduce the bug

---

## Phase 7 — Service catalog as code & GitHub

### 7.1 `servicedna.yaml` in each repo
```yaml
service: order-service
owner: team-checkout
tier: critical
slo: 99.9
health: /health
dependencies:            # optional — observed graph fills the rest
  - payment-service
alerts:
  - condition: STATUS_DOWN
    open_incident: CRITICAL
```
Generated by `sdna init`; the source of truth for metadata that traffic can't reveal (owner,
tier, SLO, alert policy).

### 7.2 GitHub App
- Install on an org → pick repos → ServiceDNA reads `servicedna.yaml` + API specs (Phase 3.3)
- Re-syncs on push; PR checks can run flow tests (Phase 5.4) and comment results
- **Import from GitHub org** for first-time setup: list repos, tick the ones to add

---

## ShopLite — test bed for every phase

[`servicedna-playground`](https://github.com/servicedna-playground) is the reference system each
feature is proven against.

| Enhancement | Proves |
|---|---|
| Add ServiceDNA/OTel SDKs to all 7 services (Node, Python, Go) | Phase 2 onboarding in three languages |
| `order-service` publishes `order.created` to Kafka, consumed by `notification-service` | Async edges, Kafka watcher, async completion detection |
| Switch `payment-service` to **gRPC** | Protocol-aware flow graph |
| Add a **GraphQL** endpoint on `api-gateway` | GraphQL operation discovery |
| Publish OpenAPI / proto / AsyncAPI specs in each repo | Static discovery & payload templates |
| Add `servicedna.yaml` to each repo | Catalog-as-code sync |
| Honour `sdna.test` in `payment-service` (skip real charge) | Safe test runs |
| Bootstrap `SDNA_PLAN=PRO` option | Plan-limit enforcement doesn't block the demo |

---

## Suggested order

| Order | Phase | Why here | Rough effort |
|---|---|---|---|
| 1 | **0** Bug fixes | Cheap, removes rough edges seen immediately | 1–2 days |
| 2 | **1.1** Alerts → incidents | Smallest change with the biggest product impact | 1–2 days |
| 3 | **2** Onboarding (OTLP ingest, SDKs, Agent, wizard) | Foundation for everything below | 3–4 weeks |
| 4 | **3** Auto dependency graph | First visible payoff of Phase 2 | 1 week |
| 5 | **4** API flow graph | Same data, new view | 1 week |
| 6 | **5** Test Studio | The differentiator | 2–3 weeks |
| 7 | **6** Bug analysis | Reuses Test Studio's hop view | 1–2 weeks |
| 8 | **7** Catalog as code & GitHub | Removes remaining manual metadata | 1–2 weeks |
| 9 | **1.2 / 1.3** Threshold alerts, plan limits | Needed before charging customers | 1 week |

---

## Prior art to learn from

- **Jaeger / Grafana Tempo** — trace storage and service graphs
- **OpenTelemetry Collector** `servicegraph` connector — edge generation
- **Tracetest** — trace-based testing and assertions (closest to Test Studio)
- **Kafka UI / Conduktor** — produce-and-inspect UX
- **Kibana / OpenSearch Dashboards** — search UX
- **Sentry / Datadog** — onboarding wizard UX
- **Backstage** — `catalog-info.yaml` service catalog
- **Grafana Beyla, Odigos, Pixie** — eBPF zero-code instrumentation
