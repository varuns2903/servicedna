# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in ServiceDNA, please report it privately rather than
opening a public issue — open a [GitHub security advisory](../../security/advisories/new) on this
repository, or contact the maintainer directly. Please include:

- A description of the vulnerability and its impact
- Steps to reproduce (a minimal example is ideal)
- Any suggested remediation, if you have one

We'll acknowledge reports as quickly as we can and credit responsible disclosures in the release
notes unless you'd prefer otherwise.

## Authentication & Authorization

- **Stateless JWT** access tokens (short-lived, `JWT_EXPIRATION_MS`) signed with a server-held
  secret, plus **rotating refresh tokens** (`JWT_REFRESH_EXPIRATION_MS`) — one active refresh
  token per user; using it issues a new pair and invalidates the old one.
- **GitHub OAuth2** and **generic OIDC SSO** are both supported. An identity provider's
  `email_verified: false` claim can create a *brand-new* account but is never sufficient to sign
  into an *existing* account on that email — this closes an account-takeover path where an
  attacker registers an OIDC identity with an unverified address matching a victim's existing
  ServiceDNA account.
- **Role-based access control**: every organization membership carries one of `OWNER`, `ADMIN`,
  `MEMBER`, `VIEWER`. Every org-scoped service method validates the caller's membership and role
  before acting. An organization can never be left without an `OWNER` — removing the last owner,
  or that owner deleting their own account, is rejected.
- **Password storage**: bcrypt via Spring Security's `PasswordEncoder`.
- **Session/token endpoints** (`/auth/login`, `/auth/register`, etc.) are covered by the same
  rate limiter as everything else, keyed by IP for unauthenticated calls, which blunts credential
  stuffing and brute-force attempts.

## API Key Authentication

Each service registered for health-check ingestion gets its own API key (`X-API-Key` header on
`POST /api/v1/ping`), independent of user JWTs. Keys can be rotated at any time from the service's
detail page — the old key stops working immediately. An invalid key is rejected with `401`.

## Rate Limiting

Every `/api/**` route (aside from `/actuator/**` and `/ws/**`) is rate-limited via a Redis-backed
fixed-window counter, keyed by authenticated user id or, for anonymous calls, by
`request.getRemoteAddr()` — deliberately **not** the client-supplied `X-Forwarded-For` header,
since trusting that would let a caller rotate it per request to get a fresh bucket every time and
defeat the limiter entirely (most importantly on `/auth/login`). Responses carry
`X-RateLimit-Limit` / `X-RateLimit-Remaining`; exceeding the configured limit
(`rate-limit.max-requests`, default 100/minute) returns `429`.

## CORS

Cross-origin requests are restricted to an explicit allow-list (`CORS_ALLOWED_ORIGINS`) rather than
a wildcard — set this to your actual deployed frontend origin(s) in production.

## Data Portability & Deletion

- `GET /api/v1/users/me/export` returns the caller's own account data, org memberships, and
  reported incidents as JSON.
- `POST /api/v1/users/me/delete-account` deactivates an account: it's removed from every
  organization it belongs to (reusing the same last-owner protection as a manual member removal),
  all sessions/tokens are revoked, and the user row is anonymized in place rather than
  hard-deleted — because `incidents.created_by` cascades on delete, a hard delete would silently
  wipe every incident that user ever reported.

## Webhook Delivery

Outbound notification webhooks (Slack/Teams/generic) only accept `https://` URLs. Delivery is
best-effort with a bounded timeout; a broken or unreachable webhook is logged, never allowed to
fail the incident operation that triggered it, and never retried indefinitely.

## Known Hardening Areas

This is an active project; the following are called out deliberately rather than left silent:

- **SSRF on health-check URLs**: service health-check probes fetch a user-supplied URL. If you're
  deploying ServiceDNA where untrusted users can register services, add network-level egress
  controls (deny `localhost`, RFC 1918 ranges, and cloud metadata endpoints like
  `169.254.169.254`) in front of the prober, since the application layer does not currently
  enforce this itself.
- **Stripe webhook signature verification** relies on `STRIPE_WEBHOOK_SECRET` being set correctly
  in every environment that receives live Stripe events — an unset secret should never be used
  outside local development.

## Reporting Coordinated Disclosure

We support and appreciate coordinated disclosure. Please give us a reasonable window to address a
confirmed issue before any public discussion.
