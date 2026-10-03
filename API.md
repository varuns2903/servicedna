# API Reference

ServiceDNA exposes a versioned REST API under `/api/v1`, plus a STOMP-over-SockJS WebSocket
endpoint for real-time dashboard updates. A full interactive OpenAPI/Swagger UI is also served by
the running backend at `http://localhost:8080/swagger-ui.html` — this document is the quick
reference; that UI is the source of truth for exact request/response schemas.

## Conventions

- **Base URL**: `/api/v1`
- **Auth**: unless listed under [Public endpoints](#public-endpoints), every route requires
  `Authorization: Bearer <accessToken>`. Most resource routes are additionally scoped to an
  organization via `/organizations/{orgId}/...` and enforce that the caller is a member of that org.
- **Roles**: organization membership has one of `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`. Mutating
  endpoints on members, billing, and on-call/escalation configuration require `OWNER` or `ADMIN`
  unless noted otherwise.
- **Rate limiting**: every `/api/**` request is rate-limited (Redis-backed, per authenticated user
  or per IP for anonymous calls). Responses carry `X-RateLimit-Limit` / `X-RateLimit-Remaining`
  headers; exceeding the limit returns `429 Too Many Requests`.

### Error envelope

```json
{
  "timestamp": "2026-03-12T12:00:00Z",
  "status": 400,
  "errorCode": "INVALID_REQUEST",
  "message": "The provided service URL is invalid.",
  "path": "/api/v1/organizations/.../services",
  "requestId": "req-12345"
}
```

An expired/invalid JWT is rejected by the security filter chain itself and comes back as a bare
`403` with **no** `errorCode` — that's how the frontend distinguishes "your session expired" (retry
after a silent token refresh) from an `ApiException`-driven `403` such as `ACCESS_DENIED`, which
always carries an `errorCode` and must never trigger a retry.

---

## API tokens — `/api/v1/users/me/tokens`

Personal tokens (`sdna_pat_…`) for the CLI and CI, sent as `Authorization: Bearer <token>`; a
token acts as its user, with the user's organization roles. Only a hash is stored.

| Method | Path | Description |
|---|---|---|
| GET | `/` | Your tokens: name, prefix, created, expires, last used (never the token) |
| POST | `/` | Create one (`{name, expiresInDays?}` — 1–366, omitted = never); the response is the only time `token` is returned |
| DELETE | `/{tokenId}` | Revoke |

Creating and revoking need a signed-in session: requests authenticated with an API token get
`403 SESSION_REQUIRED`, so a leaked token can't mint more.

## Authentication — `/api/v1/auth`

| Method | Path | Description |
|---|---|---|
| POST | `/register` | Create an account with email + password (sends a verification email) |
| POST | `/login` | Exchange email + password for an access + refresh token pair |
| POST | `/refresh` | Exchange a refresh token for a new pair (rotates the refresh token) |
| POST | `/logout` | Revoke a refresh token |
| GET | `/verify-email?token=` | Confirm a registration email |
| POST | `/resend-verification` | Re-send the verification email |
| POST | `/forgot-password` | Request a password-reset email |
| POST | `/reset-password` | Complete a password reset with a reset token |
| GET | `/sso-config` | Whether a generic OIDC provider is configured (drives the SSO button) |
| GET | `/me` | Current user (`id`, `email`, `role`) |

GitHub and generic-OIDC login are handled by Spring Security's OAuth2 login flow
(`/oauth2/authorization/github`, `/oauth2/authorization/oidc`), which redirects back to the
frontend at `/oauth2/redirect?token=...&refreshToken=...` on success.

---

## Users & Account — `/api/v1/users`

| Method | Path | Description |
|---|---|---|
| GET | `/me` | Current user profile |
| POST | `/me/change-password` | Change password (requires current password) |
| POST | `/me/change-email/request` | Request an email change (sends a confirmation link) |
| GET | `/me/change-email/confirm?token=` | Confirm the email change |
| GET | `/me/notification-preferences` | Get notification preferences |
| PATCH | `/me/notification-preferences` | Update notification preferences |
| GET | `/me/export` | Export all of the caller's data as JSON (account, org memberships, reported incidents) |
| POST | `/me/delete-account` | Deactivate the account (requires password; blocked if the caller is a sole org owner) |

Account deletion anonymizes the user row in place rather than hard-deleting it — incidents and
audit history the account is referenced from stay intact.

---

## Organizations — `/api/v1/organizations`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Create an organization (caller becomes `OWNER`) |
| GET | `/` | List organizations the caller belongs to |
| PUT | `/{orgId}` | Rename an organization |
| GET | `/{orgId}/members` | List members |
| POST | `/{orgId}/members` | *(see invites below — direct add is invite-based)* |
| PATCH | `/{orgId}/members/{memberId}` | Change a member's role |
| DELETE | `/{orgId}/members/{memberId}` | Remove a member (or leave, if removing yourself) — blocked if it would leave the org without an `OWNER` |
| POST | `/{orgId}/invites` | Invite a member by email |
| GET | `/{orgId}/invites` | List pending/accepted/expired invites |
| POST | `/invites/{token}/accept` | Accept an invite |
| GET | `/{orgId}/audit-logs` | Paginated audit log of sensitive actions in the org |

---

## Services — `/api/v1/organizations/{orgId}/services`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Register a service (returns a one-time-visible API key) |
| GET | `/` | List services |
| GET | `/{serviceId}` | Get a service |
| PUT | `/{serviceId}` | Update a service (name, URLs, region, SLO target) |
| DELETE | `/{serviceId}` | Delete a service and its ping history |
| GET | `/{serviceId}/metrics?range=24h\|7d\|30d` | Uptime %, average latency, and time-series data points |
| GET | `/map` | Full dependency graph (nodes + edges) for the org |
| PATCH | `/{serviceId}/status` | Manually set a service's status |
| POST | `/{serviceId}/api-key/regenerate` | Rotate a service's API key |
| POST | `/{serviceId}/dependencies` | Add a "depends on" edge to another service |

### API catalog

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/organizations/{orgId}/catalog/scan` | What `sdna scan` found in a repository: `service`, optional `environment`, `operations` (protocol, name, source, description, requestSchema — replaces the service's catalog; `null` keeps it) and `dependencies` (service names, added as declared edges; unknown names are reported back). From `servicedna.yaml`: `metadata` (`description`, `owner`, `tier` — critical/high/medium/low —, `slo`, `healthUrl`, `repositoryUrl`; unset fields are left alone) and `alerts` (alert rule bodies; they replace the rules the file set before, marked `managedBy: CATALOG`, and leave hand-made rules alone; omitting `alerts` leaves them be). Registers the service if it's new; returns what was `updated` and how many `alertRules` the file manages |
| GET | `/api/v1/organizations/{orgId}/services/{serviceId}/operations` | The service's operations from its specs merged with those callers used in the last 24 h (`observed`, `callsLast24h`), plus entry operations nothing instrumented calls (`callsLast24h` 0) |

### API flows — `/api/v1/organizations/{orgId}/flows`

| Method | Path | Description |
|---|---|---|
| GET | `/?windowMinutes=60[&entryNode=&entryOperation=]` | Operation-level calls (nodes are service/database/host/topic operations). With an entry, only what that operation triggers downstream. Each call has protocol, calls/min, error rate, p50 and p95 |
| GET | `/entry-points?windowMinutes=60` | Operations nothing instrumented calls (entered from outside), busiest first |

### GitHub App

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/organizations/{orgId}/github/app` | Whether the app is set up here, its install link (owners/admins; carries a signed, hour-long state), and the organization's installations |
| POST | `/api/v1/organizations/{orgId}/github/installations` | Connect an installation after GitHub returns from installing: `{installationId, code, state}`. The state must be this org's and user's; GitHub's sign-in code must show the user can administer the installation (`403` otherwise); an installation belongs to one organization (`409`). Starts a sync |
| DELETE | `/api/v1/organizations/{orgId}/github/installations/{installationId}` | Disconnect |
| POST | `/api/v1/organizations/{orgId}/github/installations/{installationId}/sync` | Apply every repository's `servicedna.yaml` now; returns each repository's result |
| POST | `/api/v1/github/webhook` | GitHub's deliveries (public; `X-Hub-Signature-256` must verify with the webhook secret, else `401`). Handles `push`, `pull_request`, `installation`, `installation_repositories` |

### Import from GitHub — `/api/v1/organizations/{orgId}/github/import`

| Method | Path | Description |
|---|---|---|
| POST | `/preview` | `{owner, token?}`: the GitHub organization's (or user's) repositories — up to 300 — with what each `servicedna.yaml` says (service, owner, tier), manifest errors, and whether the service is already registered |
| POST | `/` | `{owner, token?, repositories: ["org/repo", …]}`: registers each repository's service from its `servicedna.yaml` (as `sdna scan` would), or from the repository's name, description and URL if it has none. Each repository succeeds or fails on its own |

The GitHub token is used for the request only and never stored; for private repositories it
needs read access to contents and metadata. Set `GITHUB_API_URL` for GitHub Enterprise.

### Follow a key — `/api/v1/organizations/{orgId}/follow`

| Method | Path | Description |
|---|---|---|
| GET | `/?key=orderId&value=o-17[&from=&to=]` | Everything that touched a business key (default: the last day): traces with a span tagged `sdna.key.<key>`, an attribute `<key>`, a captured value, a Kafka message key or a captured body holding the value — separate traces included — and log lines mentioning it. The value matches whole ids (`o-1` doesn't match `o-12`). Returns the services involved, first/last seen, the traces, the logs and the queries used |

### Logs — `/api/v1/organizations/{orgId}/logs`

| Method | Path | Description |
|---|---|---|
| GET | `/` | Log search, newest first. Optional filters: `service`, `environment`, `level` (`debug`/`info`/`warn`/`error`), `text` (case-insensitive), `traceId` (a trace's logs), repeated `attribute` (`orderId=o-17`, `status>=500`), `from`/`to` (ISO; default the last hour, at most 30 days), `limit` (≤ 1000). `q` is raw LogQL and overrides the filters. Returns the LogQL used and entries with time, service, environment, level, message, trace/span ids and attributes |

Malformed LogQL returns `400 INVALID_QUERY`.

### Traces — `/api/v1/organizations/{orgId}/traces`

Read from the trace store as the organization's tenant.

| Method | Path | Description |
|---|---|---|
| GET | `/?service=&operation=[&calleeService=&calleeOperation=][&errorsOnly=][&windowMinutes=][&limit=]` | Recent traces through an operation, or where it called the callee's operation (TraceQL `{caller} >> {callee}`) |
| GET | `/{traceId}` | The trace as spans ordered by start: service, name, kind, timing, status, attributes, events |
| GET | `/explore` | Explorer search. Optional filters: `service`, `operation`, `environment`, `status` (`error`/`ok`), `minDurationMs`, `maxDurationMs`, repeated `attribute` (`orderId=o-17`, `http.response.status_code>=500`, `=~` for regex; unscoped keys match span or resource attributes), `text` (inside captured bodies), `from`/`to` (ISO; default the last hour, at most 7 days), `limit` (≤ 200). `q` is raw TraceQL and overrides the filters. Returns the TraceQL used and each trace with its matching spans and per-service span/error counts |
| GET | `/attributes` | Attribute names seen recently (resource ones prefixed `resource.`), for filter suggestions |
| GET | `/attributes/values?name=` | Recent values of one attribute |

Malformed TraceQL returns `400 INVALID_QUERY` with the trace store's parse error.

### Test runs — `/api/v1/organizations/{orgId}/test-runs`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Queue a request for a runner: `protocol` (`HTTP`, `GRAPHQL`, `GRPC`, `MESSAGING`), `serviceId` + `method` + concrete `path` (HTTP/GraphQL), `serviceId` + `grpcMethod` (`package.Service/Method`), or `topic` + `key` (messaging); `headers`, `body`, optional `environment` (defaults to the service's). Gets its own `traceId` |
| POST | `/replay` | Re-send the request a trace recorded — its entry request, or `spanId`'s — as a test run (`{traceId, spanId?, environment?, testMode?}`): HTTP method and path, gRPC method or Kafka topic and key, with the captured body. `422 NOT_REPLAYABLE` when the body wasn't captured (turn on `SERVICEDNA_CAPTURE_ON_ERROR`) |
| GET | `/` | Recent runs |
| GET | `/{runId}` | Status (`QUEUED` → `RUNNING` → `WAITING` → `COMPLETED`, or `FAILED`/`TIMED_OUT`), the entry call's `result`, and `hops`: every service the request reached (and databases/external calls it made), with what each received and returned |

A run completes when its trace stops growing (asynchronous consumers included), or after a minute.

Runs are in **test mode** unless `testMode: false`: they carry baggage `sdna.test=1`, which
services can honour to skip real side effects (charges, emails). Every run is audit-logged, and
an organization can start at most 60 a minute. Production-like environments (`prod`,
`production`, `live`, …) refuse test runs (`403 TEST_RUNS_DISABLED`) until an owner or admin
allows them:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/organizations/{orgId}/environments` | Each environment, whether it's production-like, and whether test runs are allowed |
| PUT | `/api/v1/organizations/{orgId}/environments/{environment}` | `{"allowTestRuns": true}` — `OWNER`/`ADMIN`, audit-logged |

### Collections and suites

| Method | Path | Description |
|---|---|---|
| GET / POST | `/api/v1/organizations/{orgId}/test-collections` | Saved cases: `{name, description, cases: [{name, request, assertions}]}` (a case's request can name its service with `serviceName`) |
| PUT / DELETE | `/api/v1/organizations/{orgId}/test-collections/{id}` | |
| POST | `/api/v1/organizations/{orgId}/test-suites` | Run a collection (`collectionId`) or inline `cases` (CI), optionally in an `environment` |
| GET | `/api/v1/organizations/{orgId}/test-suites[/{id}]` | Status (`RUNNING` → `PASSED`/`FAILED`), counts, and each case's run with its assertion results |

An assertion targets the entry call (`{"target": {"entry": true}}`) or a hop
(`{"target": {"service": "payment-service", "operation": "Charge"}}`, operation matched exactly or
as a substring) and checks any of `exists`, `status`, `latencyMs`, `request` / `response` fields
(dot paths such as `lines.0.qty`) and `captured` values. Expected values are literals or one of
`eq ne lt lte gt gte contains matches exists`, e.g. `"latencyMs": {"lt": 500}`. A case whose run
fails or times out fails.

### Runner — `/api/v1/runner` *(authenticated by ingestion key or `RUNNER_SHARED_TOKEN`)*

| Method | Path | Description |
|---|---|---|
| POST | `/claim` | Long-polls (20 s) for a queued run in the runner's `environment`; 204 when there's none |
| POST | `/runs/{runId}/result` | Report the entry call's outcome |

### Dependency graph — `/api/v1/organizations/{orgId}/graph`

| Method | Path | Description |
|---|---|---|
| GET | `/?windowMinutes=60` | Declared and traffic-observed dependencies (max 7 days). Nodes are services plus databases, external hosts and topics seen in traces; each edge says whether it's `declared`, `observed`, or both (drift), with protocols, calls, calls/min, error rate, average and p95 latency, and last seen |

### Health-check ingestion — `/api/v1/ping` *(public endpoint, see below)*

### Maintenance windows — `/api/v1/organizations/{orgId}/maintenance`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Schedule a maintenance window for a service |
| GET | `/` | List maintenance windows |
| PUT | `/{windowId}/status` | Update a window's status |

---

## Ingestion Keys — `/api/v1/organizations/{orgId}/ingestion-keys`

Organization-wide keys for sending telemetry (OTLP traces, pushed health checks) from SDKs,
OpenTelemetry collectors and the ServiceDNA agent. Services identify themselves through their
telemetry, so one key per environment or collector is enough. Only a hash is stored.

| Method | Path | Description |
|---|---|---|
| GET | `/` | List keys (name, prefix, last used, revoked) — never the key itself |
| POST | `/` | Create a key (`name`); the response is the only time `key` is returned. `OWNER`/`ADMIN` only |
| DELETE | `/{keyId}` | Revoke a key. `OWNER`/`ADMIN` only |

---

## Incidents — `/api/v1/organizations/{orgId}/incidents`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Report an incident (pages on-call for CRITICAL/MAJOR, notifies opted-in members, fires webhooks) |
| GET | `/` | List incidents |
| GET | `/{incidentId}` | Get an incident |
| PATCH | `/{incidentId}/status` | Transition status (`INVESTIGATING` → `IDENTIFIED` → `MONITORING` → `RESOLVED`) |
| POST | `/{incidentId}/acknowledge` | Acknowledge (stops the escalation job from paging past you) |
| PUT | `/{incidentId}/post-mortem` | Upsert the post-mortem (root cause / timeline / action items) — only once resolved |
| GET | `/{incidentId}/post-mortem` | Get the post-mortem |
| GET | `/{incidentId}/events` | Auto-built event timeline (created, acknowledged, status changes, escalated, post-mortem updates) |
| GET | `/{incidentId}/failing-traces` | Failing traces through the affected services (any service if none) from 15 min before the incident opened until it resolved |
| GET / POST | `/{incidentId}/traces` | Attached traces / attach one (`{traceId, note}`): its hops are snapshotted so the failure path outlives trace retention, and a `TRACE_ATTACHED` timeline event says where it failed. `409` if already attached |
| DELETE | `/{incidentId}/traces/{traceId}` | Detach |

---

## On-Call & Escalation

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/organizations/{orgId}/on-call` | Current rotation config + who's on call right now |
| PUT | `/api/v1/organizations/{orgId}/on-call` | Configure the rotation (length, start date, ordered member list) |
| GET | `/api/v1/organizations/{orgId}/escalation-policy` | Get the escalation policy |
| PUT | `/api/v1/organizations/{orgId}/escalation-policy` | Set the escalation email + unacknowledged-minutes threshold |

A scheduled job (`ESCALATION_CHECK_CRON`, default every 5 minutes) pages the escalation contact for
any CRITICAL/MAJOR incident that's gone unacknowledged past the configured window.

---

## Alerts — `/api/v1/organizations/{orgId}/services/{serviceId}/alert-rules`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Create an alert rule: `condition` (`STATUS_DOWN` \| `STATUS_DEGRADED` \| `STATUS_RECOVERED` \| `LATENCY_ABOVE` \| `ERROR_RATE_ABOVE` \| `CONSECUTIVE_FAILURES` \| `UNDECLARED_DEPENDENCY` — the service starts calling a service it doesn't declare, the first time that's seen; the three before it need `threshold` — ms, percent, or a check count — and the first two of those `windowMinutes`, 1–60), and at least one action — `webhookUrl` + `integrationType` (`GENERIC` \| `SLACK` \| `DISCORD`), and/or `incidentSeverity` (`CRITICAL` \| `MAJOR` \| `MINOR` \| `LOW`; not allowed on `STATUS_RECOVERED`) |
| GET | `/` | List alert rules for a service |
| DELETE | `/{ruleId}` | Delete a rule |

Threshold rules are evaluated every 30 seconds (`ALERT_THRESHOLD_EVALUATION_INTERVAL_MS`). When
the service sends traces and handled at least `ALERT_THRESHOLD_MIN_REQUESTS` (default 20) requests
in the window, `LATENCY_ABOVE` is the p95 latency of those requests (its server and consumer spans)
and `ERROR_RATE_ABOVE` the share that failed (span status ERROR — a 5xx, not a 4xx). Otherwise
they fall back to recent checks from the active prober and push agents: `LATENCY_ABOVE` averages
successful checks and `ERROR_RATE_ABOVE` is the share of DOWN checks, given at least
`ALERT_THRESHOLD_MIN_SAMPLES` (default 3). `CONSECUTIVE_FAILURES` always looks at the latest N
checks. Alert messages say which they judged.
Each rule fires once when crossed and once when cleared (`breached` on the rule shows its state).

Status-change evaluation runs asynchronously off a Kafka consumer: when a service's status changes, every
rule matching the new status posts to its webhook (suppressed while the service is in an active
maintenance window). Rules with an `incidentSeverity` also open an incident: a service has at most
one open alert-opened incident — repeat alerts add to its timeline and can raise (never lower) its
severity. Cascading failures share one incident: a service joins the open alert incident of
anything it depends on, or that depends on it, and the incident is retitled after the likeliest
root cause. It resolves automatically once every affected service is healthy again. CRITICAL/MAJOR
alert incidents page on-call and escalate like any other incident.

---

## Notification Webhooks — `/api/v1/organizations/{orgId}/webhooks`

| Method | Path | Description |
|---|---|---|
| GET | `/` | List configured webhooks |
| POST | `/` | Add a webhook (`url`, `webhookType`: `SLACK` \| `TEAMS` \| `GENERIC`; `https://` only) |
| DELETE | `/{webhookId}` | Remove a webhook |

Incident created, resolved, and escalated events post to every configured webhook, formatted per
type. Delivery is best-effort — a broken or unreachable webhook is logged and never fails the
incident operation that triggered it.

---

## Analytics — `/api/v1/organizations/{orgId}/analytics`

| Method | Path | Description |
|---|---|---|
| GET | `/sla?days=30` | SLA report: overall uptime, incident count, MTTR, MTBF, and per-service error-budget burn-down against each service's configured SLO target |

---

## Dashboard — `/api/v1/organizations/{orgId}/dashboard`

| Method | Path | Description |
|---|---|---|
| GET | `/` | Aggregated summary (service counts by status, active incidents) — also pushed live over WebSocket on any change |

**WebSocket**: connect to `/ws` (SockJS/STOMP) and subscribe to
`/topic/organizations/{orgId}/dashboard` for push updates whenever a service status or incident
changes, instead of polling this endpoint.

---

## Billing — `/api/v1/organizations/{orgId}/billing`

| Method | Path | Description |
|---|---|---|
| GET | `/subscription` | Current plan and status |
| POST | `/checkout-session` | Create a Stripe Checkout session for a plan upgrade |

---

## Telemetry ingestion (OTLP)

ServiceDNA accepts OpenTelemetry traces and logs, authenticated by an [ingestion key](#ingestion-keys--apiv1organizationsorgidingestion-keys)
in the `x-servicedna-key` header (or `Authorization: Bearer <key>`). Spans are stored in Grafana
Tempo and logs in Grafana Loki, one tenant per organization.

| Protocol | Endpoint | Notes |
|---|---|---|
| OTLP/HTTP (protobuf) | `POST /api/v1/otlp/v1/traces` on the backend | Set `OTEL_EXPORTER_OTLP_ENDPOINT=<backend>/api/v1/otlp`. gzip supported; 16 MB per batch. Returns `401` for a bad key and `503` (retry) if trace storage is down |
| OTLP/HTTP logs (protobuf) | `POST /api/v1/otlp/v1/logs` on the backend | Same key, encoding and limits; `503` (retry) if log storage is down. Records keep their trace and span ids |
| OTLP/gRPC | `:4317` on the bundled collector | Forwarded to the endpoint above |
| OTLP/HTTP | `:4318` on the bundled collector | Forwarded to the endpoint above |
| Zipkin v2 JSON | `:9412/api/v2/spans` on the bundled collector | Forwarded to the endpoint above |

Services register themselves from this telemetry: the first spans from a `service.name` (within
its `deployment.environment`, if set) create the service, and `telemetry.sdk.language`,
`service.version`, `cloud.region` and `servicedna.health.url` keep its details current.

Through the collector, a bad key surfaces only in the collector's logs (the data is dropped, not
retried); send straight to the backend to see authentication errors at the client.

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:8080/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_ik_...
OTEL_SERVICE_NAME=checkout-service
```

---

## Public endpoints

These require no authentication:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/public/organizations/{orgId}/status` | Public status page data (service health + active incidents) |
| POST | `/api/v1/runner/claim`, `/api/v1/runner/runs/{id}/result` | Test Studio runners — authenticated by ingestion key or `RUNNER_SHARED_TOKEN` |
| POST | `/api/v1/otlp/v1/traces` | OTLP trace ingestion — authenticated via an ingestion key, see [Telemetry ingestion](#telemetry-ingestion-otlp) |
| POST | `/api/v1/otlp/v1/logs` | OTLP log ingestion, likewise |
| POST | `/api/v1/ping` | Health-check ingestion — authenticated via an `X-API-Key` header: either a service's own API key, or an organization ingestion key with `service` (and optional `environment`) in the body, which registers the service on first contact |
| POST | `/api/v1/webhooks/stripe` | Stripe webhook receiver — authenticated via Stripe's signature header |
| GET | `/api/v1/auth/sso-config`, `/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password` | See [Authentication](#authentication--apiv1auth) |
