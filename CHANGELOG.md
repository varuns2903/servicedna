# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Import from GitHub (Services → Import from GitHub): list a GitHub organization's repositories,
  see what each `servicedna.yaml` declares, and register the chosen ones in one go — from their
  manifest, or from the repository's name, description and link. The token isn't stored.
- GitHub Action (`integrations/github-action`): on pushes to the default branch it runs
  `sdna scan`; on pull requests it runs the repository's test flows and reports them in the job
  summary and a single, updated PR comment, failing the check when a case fails. `sdna test run
  --report FILE` writes the Markdown it posts.
- API tokens (`sdna_pat_…`): created in Settings → Account, they act as their user from the CLI
  (`sdna login --token`, for SSO accounts too) and CI (`SDNA_URL`, `SDNA_TOKEN`, `SDNA_ORG`, no
  login or config file). Only hashes are stored; tokens can expire, show when they were last
  used, and can't mint more tokens.
- Catalog as code: a repository's `servicedna.yaml` declares its service's description, owner,
  tier, SLO, health URL, repository, dependencies and alert rules; `sdna scan` applies it and
  `sdna init` writes a starter. Alert rules it declares are managed by the file (replaced on each
  scan, badged in the UI); hand-made rules are left alone. Services gain `owner` and `tier`,
  shown in the service list and details.
- Incidents show their traces: failing requests through the affected services while the incident
  was open, and traces attached as evidence with a note. Attaching snapshots the request's hops
  (so the post-mortem keeps the failure path after trace retention) and adds a timeline entry
  saying where it failed. Any trace can be replayed — rebuilt from its captured request and sent
  through a runner, usually into a non-production environment — from the incident page or the
  trace explorer. API: `/incidents/{id}/failing-traces`, `/incidents/{id}/traces`,
  `POST /test-runs/replay`.
- Follow a business key (`/follow`): every request, separate trace and log line that touched an
  id like `orderId=o-17`, in time order — so work that lost its trace context (queues, batch
  jobs, background workers) is still connected. SDKs gain `tag(name, value)` (Node.js, Python,
  Go `Tag(ctx, …)`, Spring Boot `ServiceDna.tag`), and tagged values in span details link to the
  page. API: `GET /follow?key=&value=`.
- Logs. `POST /api/v1/otlp/v1/logs` ingests OTLP logs (stored in Grafana Loki, one tenant per
  organization; the bundled collector and the agent forward OTLP/gRPC logs, and the agent masks
  them like spans). The SDKs send logs by default — Node.js console/pino/bunyan, Python logging
  (uvicorn included), Go slog and log, Spring Boot Logback — tagged with the active trace.
  `/logs` searches them by service, environment, level, text or attribute (or LogQL), each line
  linking to its trace; traces in the explorer and Test Studio runs show the logs written while
  handling them. `docker-compose.yml` gains Loki and `LOG_STORE_URL`.
- Trace explorer (`/traces`): search every trace by service, operation, environment, status,
  latency, any attribute (`orderId=o-17`, `http.response.status_code>=500`) or text inside
  captured bodies — or write TraceQL. Results show the matching spans and which services erred;
  opening one shows its waterfall at the span that matched, with captured bodies laid out.
  Searches live in the URL, so they can be shared. API: `GET /traces/explore`,
  `/traces/attributes`, `/traces/attributes/values`.
- SDKs: capture on error. With `SERVICEDNA_CAPTURE_ON_ERROR=true` (Spring Boot:
  `servicedna.capture-on-error`), request and response bodies are held per request and recorded
  only when it fails (5xx or an unhandled exception; in Node.js any span ending in error, including
  failed Kafka consumers), masked and marked `sdna.captured_on_error`. Off by default.
- Test Studio (`/testing`): compose an HTTP, GraphQL, gRPC or Kafka request from a service's
  catalog (body templated from its request schema), send it through the environment's runner and
  watch each hop arrive with what it received and returned. One click turns a run into
  assertions; send-and-check evaluates them; cases save to collections, which run as suites with
  per-check results. Settings → Integrations gains an Environments card to allow test runs in
  production-like environments.
- A service's operations now include its entry operations — ones only uninstrumented callers
  (browsers, cron, the runner) use — so the gateway's endpoints can be picked in Test Studio.
- Alert rules can open incidents. Set `incidentSeverity` on a `STATUS_DOWN` or `STATUS_DEGRADED`
  rule and a matching status change opens an incident (paging on-call for CRITICAL/MAJOR, and
  escalating if unacknowledged) — no human needed. A service has at most one open alert incident:
  repeat alerts update its timeline and can raise its severity, and it resolves automatically when
  the service recovers. Webhooks are now optional on a rule that opens incidents. Incidents opened
  this way have no reporter and carry `triggeredByServiceId`; the UI marks them "Auto-opened".
- Threshold alert conditions: `LATENCY_ABOVE` (average latency of successful checks over a
  window), `ERROR_RATE_ABOVE` (percentage of failed checks over a window), and
  `CONSECUTIVE_FAILURES` (the latest N checks all failed). Evaluated every 30 seconds; each rule
  fires once when crossed and once when cleared, with the same webhook and incident actions as
  status rules. An incident a threshold opened resolves once the metric clears and the service is
  healthy; a service with a breached threshold rule doesn't count as recovered.
- Cascading failures are grouped into one incident. When a service goes unhealthy while
  something it depends on (or that depends on it, transitively) already has an open alert
  incident, it joins that incident instead of opening another, and the incident is retitled after
  the deepest failing dependency — the likely root cause. The incident resolves only once every
  affected service has recovered; partial recoveries appear on the timeline.

- Plan limits are enforced: Free allows 3 services and keeps 1 day of health-check history, Pro 50
  services and 30 days, Enterprise is unlimited (capped by `PING_RETENTION_DAYS`). Creating a
  service over the limit returns `403 PLAN_LIMIT_REACHED`; organizations already over it keep
  their services. `GET /billing/usage` reports plan, usage and limits, shown on the billing page.
  `BILLING_ENFORCE_PLAN_LIMITS=false` turns limits off for self-hosted deployments.
- "Manage Billing Portal" opens a real Stripe Customer Portal session
  (`POST /billing/portal-session`) instead of a placeholder URL.

- Organization ingestion keys (Settings → Integrations): one `sdna_ik_…` key per environment or
  collector for sending telemetry, stored hashed, shown once, revocable, and audit-logged.

- OpenTelemetry trace ingestion: `POST /api/v1/otlp/v1/traces` accepts OTLP/HTTP protobuf
  (gzip or plain) authenticated by an ingestion key, and docker-compose adds an OpenTelemetry
  Collector for OTLP/gRPC (`:4317`), OTLP/HTTP (`:4318`) and Zipkin (`:9412`). Spans are stored in
  Grafana Tempo with each organization as its own tenant.

- Services register themselves from their telemetry. The first spans (or organization-key ping)
  from a service create it, keyed by `service.name` within `deployment.environment`; later
  telemetry keeps its language, version, health URL and last-seen time current. A manually
  registered service of the same name without an environment is adopted rather than duplicated.
  Plan limits apply: past the limit a service isn't registered, but its spans are still stored.
- `POST /api/v1/ping` accepts an organization ingestion key with a `service` (and optional
  `environment`) in the body, so SDK heartbeats need no per-service key.
- Services list and details show environment, language/version, and whether a service was
  registered via telemetry, with an environment filter.

- SDKs that connect a service with one dependency and two environment variables
  (`SERVICEDNA_URL`, `SERVICEDNA_KEY`): `@servicedna/node`, `servicedna` for Python
  (`servicedna-run`), a Go module with `net/http` wrappers, and a Spring Boot starter. Each wraps
  OpenTelemetry, registers the service with its environment/language/version, sends traces, and
  reports health heartbeats. See `sdks/`.

- ServiceDNA agent (`agent/`): an OpenTelemetry Collector for Docker hosts (compose) and
  Kubernetes (Helm chart) that accepts OTLP and Zipkin from services without keys, stamps the
  environment, masks credentials and card numbers inside your network, and forwards to ServiceDNA
  over an outbound connection.

- "Connect a service" onboarding (`/connect`, linked from Services): create an ingestion key,
  copy a ready-to-run snippet for Node.js, Python, Go, Spring Boot, plain OpenTelemetry or the
  agent (URL, key and environment filled in), and the page waits live until the service's first
  telemetry arrives, then links to it.

- `sdna` CLI (`cli/`): `login`, `orgs`/`use`, `status`, `keys list|create`, and `init`, which
  detects a Node, Python, Go or Spring Boot project, creates an ingestion key, adds the SDK, and
  writes a gitignored `.env.servicedna`. Binaries are built for tagged `cli-v*` releases.

- The dependency graph is discovered from traffic. Client/producer spans are paired with the
  server/consumer spans they caused (across services' separate exports), aggregated per minute at
  operation level, and served by `GET /graph` with calls/min, error rate and p95 per edge. Calls
  that nothing instrumented answered become database, external-host or topic nodes — or, when the
  hostname is a registered service's name, calls to that service. Declared edges are kept and
  compared: `declared`-only edges were never seen, `observed`-only edges were never declared.

- The Dependency Graph page shows declared and observed dependencies together: edges labelled
  with calls/min, p95 and error rate; orange dashed for traffic nobody declared, dotted for
  declared edges not seen in the window; databases, external hosts and topics as their own nodes;
  window and view pickers; drift chips that highlight each kind. Incident grouping (1.1b) now also
  follows dependencies observed in the last 24 hours.

- `sdna scan` builds each service's API catalog from its OpenAPI, `.proto` and AsyncAPI specs
  (request schemas included) and declares dependencies found in its configuration — `.env`,
  Spring config, Kubernetes/Helm manifests, and per-service environments in docker-compose — so
  the graph exists before any traffic. `GET /services/{id}/operations` merges the catalog with
  operations seen in traffic.

- The Kubernetes agent tags spans with pod, deployment, namespace and node, and registers
  workloads that report `unknown_service` under their Deployment/StatefulSet/DaemonSet name
  (read-only RBAC included; `kubernetesMetadata.enabled=false` turns it off). The agent README
  covers sending Istio, Linkerd and Envoy traces to it.

- API Flows page: pick an entry point (an operation called from outside, busiest first) to see
  every call it triggers, operation by operation across services, databases and topics, with
  protocol, calls/min, p50/p95 and error rate — or view the whole operation map. Clicking a call
  lists recent traces where one operation called the other (errors-only filter) and opens any of
  them as a waterfall with span attributes and events; internal spans are hidden by default.
  Backed by `GET /flows`, `/flows/entry-points`, `/traces` and `/traces/{id}`.

- SDKs capture request and response bodies on server spans for ServiceDNA test runs (baggage
  `sdna.capture=1` only), masking credential-like fields and capping size, and expose
  `capture(name, value)` for values computed inside a function.

- Test runs and the runner (`runner/`): a run queues a request (HTTP, GraphQL, gRPC via server
  reflection, or Kafka); a runner inside the target environment claims it over an outbound
  connection, sends it with its own trace id and `sdna.capture=1` baggage, and reports the entry
  result. The run completes once its trace stops growing, and lists every hop with what each
  service received and returned, plus `capture()` values. Runners authenticate with an ingestion
  key; the one bundled with ServiceDNA (`--profile runner`) with `RUNNER_SHARED_TOKEN`.

- Test-run safety: runs are in test mode by default (baggage `sdna.test=1` for services to skip
  real side effects); production-like environments refuse test runs until an owner or admin
  allows them (`/environments`); every run is audit-logged; 60 runs a minute per organization.

- Test collections, suites and assertions: save cases, run them (or inline cases from CI) as a
  suite, and check the entry response and any hop — status, latency, request/response fields,
  captured values, whether a hop happened. `sdna test run flows/` runs YAML flow files from a repo
  and exits non-zero on failure.

### Changed
- Trace search results mark a trace as an error when any of its spans failed, not only when the
  search asked for errors.
- Spring Boot SDK: body capture writes responses straight through, keeping a capped copy,
  instead of buffering the whole response.
- Test run hops are returned in call order (callers before what they call) rather than by start
  time, which clock skew between hosts could scramble.
- A service's status now changes only after `HEALTH_CHECK_STATUS_CONFIRMATIONS` (default 2)
  consecutive observations agree, so a single slow probe, or the active prober and a push agent
  disagreeing near the DEGRADED threshold, no longer flips it back and forth. A newly registered
  service's first status still applies immediately. Set it to `1` for the previous behaviour.
- Alert webhook messages name the service and its previous status
  (`Service 'order-service' changed status from HEALTHY to DOWN`), and generic webhook payloads
  include a `serviceName` field.

### Fixed
- Runner: gRPC calls to servers that only speak reflection v1alpha (Python's grpcio) could fail
  with `EOF` — the v1 probe's rejection sometimes surfaces on Send, which is now read from Recv.
- Stripe checkout redirected to `http://localhost:5173` whatever `FRONTEND_URL` was set to.
- `docker-compose.yml` never passed the `STRIPE_*` variables to the backend, so billing couldn't
  work when running everything in Docker.
- Services page and Alerts page crashed when a service had alert rules (`Cannot read properties
  of undefined (reading 'replace')`): the frontend expected a different alert-rule shape than the
  API returns. Creating rules from the UI failed for the same reason.
- Concurrent observations of the same status change (prober and pushed ping at once) could both
  publish an alert; status changes are now applied under a row lock.
- Status changes reported through `POST /api/v1/ping` didn't evict the services cache, so the UI
  could show a stale status for up to 10 minutes.
- Client errors — malformed JSON, invalid enum values, missing headers, invalid path IDs,
  unsupported methods — returned `500` instead of the matching `4xx`.
- The Kafka healthcheck in `docker-compose.yml` ignored `KAFKA_PORT`, so changing it left Kafka
  unhealthy and the backend never started.
- Docs described alert conditions and automatic incident creation that don't exist yet.

## [1.0.0] — 2026-09-12

Initial public release.

### Added

**Core platform**
- Service registry with health-check ingestion via per-service API keys, active health-check
  polling, and a live interactive dependency graph.
- Full incident lifecycle: manual or alert-triggered creation, status transitions, acknowledgement,
  structured post-mortems (root cause / timeline / action items), and an auto-built event timeline.
- Configurable per-service alert rules (latency, error rate, consecutive failures) that
  automatically open incidents via an async Kafka-driven evaluation pipeline.
- Real-time dashboard and public, unauthenticated status pages, both pushed live over
  WebSocket/STOMP.
- Multi-tenant organizations with role-based access control (Owner/Admin/Member/Viewer), email
  invites, and an audit log of sensitive actions.
- Stripe billing integration (Free/Pro/Enterprise tiers, checkout sessions, billing portal).

**On-call, escalation, and notifications**
- Rotating on-call scheduling per organization, with automatic paging of the current on-call
  member on CRITICAL/MAJOR incidents, and a color-coded monthly calendar view.
- Escalation policies that page a fallback contact when a critical incident goes unacknowledged
  past a configurable threshold.
- Org-level notification webhooks (Slack, Microsoft Teams, or generic JSON) firing on incident
  created/resolved/escalated events, delivered best-effort alongside existing email paths.
- Per-user notification preferences for new-incident emails.

**Observability & SLAs**
- Historical uptime/latency metrics per service (24h/7d/30d windows).
- Per-service configurable SLO targets with SLA reporting: overall uptime, MTTR, MTBF, and a
  per-service error-budget burn-down against each target.

**Authentication & account management**
- Email/password auth with verification and password reset, GitHub OAuth2, and generic OIDC SSO.
- JWT access tokens with rotating refresh tokens so sessions survive access-token expiry.
- Self-service account settings: change password/email, notification preferences, full JSON data
  export, and account deletion (soft-deleted/anonymized in place so incident history and audit
  trails stay intact).

**Productivity & UX**
- Command palette (`Ctrl+K`) with fuzzy search across services/incidents and inline quick actions
  (acknowledge/resolve an incident without navigating away).
- Optimistic UI updates for incident status changes and acknowledgement.
- CSV bulk import/export for services and bulk status updates for incidents.
- Search and filtering across services and incidents lists.
- Mobile-responsive layouts with an off-canvas navigation drawer.

**Operations**
- Interactive OpenAPI/Swagger documentation served by the running backend.
- Containerized deploy for backend and frontend via `docker-compose.yml`.
- Daily retention job pruning old health-check ping history.
- Redis-backed rate limiting on every API route, keyed by user or IP.
- Configurable CORS allow-list.

### Fixed

- Rate-limit bypass via a spoofed `X-Forwarded-For` header — anonymous rate limiting now reads
  the connection's real remote address.
- Service API keys were being returned on every read, not just at creation — reads now redact
  the key.
- Dependency graph rendering as a blank canvas under certain data shapes.
- The post-mortem form posted a single `content` field while the backend always required
  structured `rootCause`/`timeline`/`actionItems`, so saving one always failed — the frontend now
  matches the backend's shape.
- A Redis cache-serialization bug (Jackson's `NON_FINAL` default typing never embeds a type id for
  `record` classes, which are implicitly final) that could 500 on reads of any cached DTO with a
  nested typed collection after the DTO's shape changed.

### Security

- SSO sign-in can no longer link to an *existing* account on an identity provider's unverified
  email claim — only new-account creation is unaffected by verification status, closing an
  account-takeover path.
- `GET /auth/me` no longer returns the caller's bcrypt password hash (or other internal fields) —
  it returns the same minimal profile shape as `GET /users/me`.

[1.0.0]: https://github.com/varuns2903/servicedna/releases/tag/v1.0.0
