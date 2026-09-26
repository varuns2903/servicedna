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
| POST | `/` | Create an alert rule: `condition` (`STATUS_DOWN` \| `STATUS_DEGRADED` \| `STATUS_RECOVERED` \| `LATENCY_ABOVE` \| `ERROR_RATE_ABOVE` \| `CONSECUTIVE_FAILURES`; the last three need `threshold` — ms, percent, or a check count — and the first two of those `windowMinutes`, 1–60), and at least one action — `webhookUrl` + `integrationType` (`GENERIC` \| `SLACK` \| `DISCORD`), and/or `incidentSeverity` (`CRITICAL` \| `MAJOR` \| `MINOR` \| `LOW`; not allowed on `STATUS_RECOVERED`) |
| GET | `/` | List alert rules for a service |
| DELETE | `/{ruleId}` | Delete a rule |

Threshold rules are evaluated every 30 seconds (`ALERT_THRESHOLD_EVALUATION_INTERVAL_MS`) against
recent checks from both the active prober and push agents: `LATENCY_ABOVE` averages successful
checks, `ERROR_RATE_ABOVE` is the share of DOWN checks, and `CONSECUTIVE_FAILURES` looks at the
latest N. Windowed rules need at least `ALERT_THRESHOLD_MIN_SAMPLES` (default 3) checks to judge.
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

ServiceDNA accepts OpenTelemetry traces, authenticated by an [ingestion key](#ingestion-keys--apiv1organizationsorgidingestion-keys)
in the `x-servicedna-key` header (or `Authorization: Bearer <key>`). Spans are stored in Grafana
Tempo, one tenant per organization.

| Protocol | Endpoint | Notes |
|---|---|---|
| OTLP/HTTP (protobuf) | `POST /api/v1/otlp/v1/traces` on the backend | Set `OTEL_EXPORTER_OTLP_ENDPOINT=<backend>/api/v1/otlp`. gzip supported; 16 MB per batch. Returns `401` for a bad key and `503` (retry) if trace storage is down |
| OTLP/gRPC | `:4317` on the bundled collector | Forwarded to the endpoint above |
| OTLP/HTTP | `:4318` on the bundled collector | Forwarded to the endpoint above |
| Zipkin v2 JSON | `:9412/api/v2/spans` on the bundled collector | Forwarded to the endpoint above |

Through the collector, a bad key surfaces only in the collector's logs (the data is dropped, not
retried); send straight to the backend to see authentication errors at the client.

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:8080/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_...
OTEL_SERVICE_NAME=checkout-service
```

---

## Public endpoints

These require no authentication:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/public/organizations/{orgId}/status` | Public status page data (service health + active incidents) |
| POST | `/api/v1/otlp/v1/traces` | OTLP trace ingestion — authenticated via an ingestion key, see [Telemetry ingestion](#telemetry-ingestion-otlp) |
| POST | `/api/v1/ping` | Health-check ingestion — authenticated via an `X-API-Key` header (a service's own API key), not a user JWT |
| POST | `/api/v1/webhooks/stripe` | Stripe webhook receiver — authenticated via Stripe's signature header |
| GET | `/api/v1/auth/sso-config`, `/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password` | See [Authentication](#authentication--apiv1auth) |
