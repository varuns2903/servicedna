# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Alert rules can open incidents. Set `incidentSeverity` on a `STATUS_DOWN` or `STATUS_DEGRADED`
  rule and a matching status change opens an incident (paging on-call for CRITICAL/MAJOR, and
  escalating if unacknowledged) — no human needed. A service has at most one open alert incident:
  repeat alerts update its timeline and can raise its severity, and it resolves automatically when
  the service recovers. Webhooks are now optional on a rule that opens incidents. Incidents opened
  this way have no reporter and carry `triggeredByServiceId`; the UI marks them "Auto-opened".
- Cascading failures are grouped into one incident. When a service goes unhealthy while
  something it depends on (or that depends on it, transitively) already has an open alert
  incident, it joins that incident instead of opening another, and the incident is retitled after
  the deepest failing dependency — the likely root cause. The incident resolves only once every
  affected service has recovered; partial recoveries appear on the timeline.

### Changed
- A service's status now changes only after `HEALTH_CHECK_STATUS_CONFIRMATIONS` (default 2)
  consecutive observations agree, so a single slow probe, or the active prober and a push agent
  disagreeing near the DEGRADED threshold, no longer flips it back and forth. A newly registered
  service's first status still applies immediately. Set it to `1` for the previous behaviour.
- Alert webhook messages name the service and its previous status
  (`Service 'order-service' changed status from HEALTHY to DOWN`), and generic webhook payloads
  include a `serviceName` field.

### Fixed
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
