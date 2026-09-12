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
| POST | `/` | Create an alert rule (latency threshold, error rate, or consecutive-failure count) |
| GET | `/` | List alert rules for a service |
| DELETE | `/{ruleId}` | Delete a rule |

Alert evaluation runs asynchronously off a Kafka consumer and opens an incident automatically when
a rule's condition is met.

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

## Public endpoints

These require no authentication:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/public/organizations/{orgId}/status` | Public status page data (service health + active incidents) |
| POST | `/api/v1/ping` | Health-check ingestion — authenticated via an `X-API-Key` header (a service's own API key), not a user JWT |
| POST | `/api/v1/webhooks/stripe` | Stripe webhook receiver — authenticated via Stripe's signature header |
| GET | `/api/v1/auth/sso-config`, `/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password` | See [Authentication](#authentication--apiv1auth) |
