# Architecture

ServiceDNA is a modular-monolith Spring Boot backend behind a React SPA, with Kafka for async
event processing, Redis for caching/rate-limiting, and PostgreSQL as the system of record.

## System Overview

```text
                          ┌───────────────────┐
                          │   React SPA (Vite) │
                          └─────────┬──────────┘
                                    │
                     REST (Axios)  │  WebSocket (STOMP/SockJS)
                                    │
                          ┌─────────▼──────────┐
                          │  Spring Boot API   │
                          │   (single JVM,     │
                          │  modular packages) │
                          └─────────┬──────────┘
                                    │
        ┌───────────────┬──────────┼──────────┬───────────────┐
        │               │          │          │               │
        ▼               ▼          ▼          ▼               ▼
   PostgreSQL         Redis      Kafka     Zipkin        Stripe / SMTP /
  (system of      (cache, rate  (async    (tracing)      Slack / Teams
    record)         limiting,   events)                  (outbound HTTP)
                   API-key      
                    lookup)     
```

## Request flow

1. **Frontend → Backend**: the SPA calls `/api/v1/...` with a short-lived JWT access token in
   `Authorization: Bearer`. `JwtAuthenticationFilter` validates it before Spring Security's
   authorization rules run; an expired/invalid token yields a bare `403` (no `errorCode`), which
   the frontend's Axios interceptor recognizes and silently retries once after refreshing the
   token via `/auth/refresh` — never on an `ApiException`-driven `403` (e.g. `ACCESS_DENIED`),
   which always carries an `errorCode` and must propagate as a real authorization decision.
2. **Controller → Service → Repository**: each module (`incident`, `service`, `organization`, …)
   follows the same three-layer shape — a thin `@RestController`, an `@Service` holding
   transactional business logic and calling `organizationService.validateUserAccess(...)` to
   enforce tenant isolation, and Spring Data JPA repositories over PostgreSQL.
3. **Cross-module calls go through service classes, not repositories** — e.g. `IncidentService`
   calls `OnCallService.getCurrentOnCallEmail(...)` to page whoever's on call, rather than
   querying the on-call tables directly. This keeps each module's persistence details private.

## Event-driven paths

- **Health-check ingestion**: external services `POST /api/v1/ping` with their own API key
  (validated against the `services` table, cached in Redis for an hour so every ping doesn't hit
  Postgres). A status change publishes a `ServiceStatusChangedEvent` onto Kafka.
- **Alert evaluation**: `AlertEventConsumer` consumes those Kafka events, evaluates configured
  alert rules (latency threshold, error rate, consecutive failures) against recent pings, and
  opens an incident automatically when a rule's condition is met.
- **Dashboard live updates**: any incident or service-status change publishes a
  `DashboardInvalidationEvent` in-process, which the dashboard module turns into a STOMP broadcast
  to `/topic/organizations/{orgId}/dashboard` — the frontend's WebSocket provider invalidates the
  relevant TanStack Query cache entries on receipt instead of polling.
- **Notification fan-out**: incident creation/resolution/escalation triggers three independent,
  best-effort notification paths — email (on-call paging, opted-in members), org-level webhooks
  (Slack/Teams/generic), and the in-app event timeline — none of which can fail the incident
  operation itself; each is wrapped so a broken SMTP server or unreachable webhook only logs a
  warning.

## Scheduled jobs

| Job | Default schedule | Purpose |
|---|---|---|
| `IncidentEscalationJob` | every 5 minutes (`ESCALATION_CHECK_CRON`) | Pages the escalation contact for any CRITICAL/MAJOR incident unacknowledged past its policy's threshold |
| `PingRetentionService` | daily at 03:00 (`PING_RETENTION_CRON`) | Prunes health-check ping history older than `PING_RETENTION_DAYS` |
| `HealthCheckProberService` | every 30s (`health-check.interval-ms`) | Actively probes any service with a configured health-check URL |

## Caching

Redis-backed `@Cacheable` methods (e.g. the services list/detail lookups) use
`GenericJackson2JsonRedisSerializer` with Jackson's `EVERYTHING` default typing — this matters
specifically because cached DTOs are Java `record`s, which are implicitly `final`; typing modes
that skip finals (`NON_FINAL`) never embed a type id for the record itself, so the deserializer
falls back to a generic `Object`/`Map` and can choke on nested typed fields (e.g. a
`List<UUID>`). If you add a new `@Cacheable` DTO, keep it a plain record/POJO — the `EVERYTHING`
typing handles it correctly either way.

## Security model

- **AuthN**: stateless JWT access tokens (short-lived) plus rotating refresh tokens (one active
  per user, invalidated and replaced on every use) for GitHub OAuth2, generic OIDC, and
  password-based login. An OIDC provider's unverified email claim can create a *new* account but
  can never sign into an *existing* one — see [SECURITY.md](SECURITY.md).
- **AuthZ**: every org-scoped service method calls `OrganizationService.validateUserAccess`, which
  checks the caller's `OrganizationMember` row exists and, where relevant, that their role
  (`OWNER`/`ADMIN`/`MEMBER`/`VIEWER`) permits the action. An org is never left without an `OWNER`.
- **Rate limiting**: a `HandlerInterceptor` backed by a Redis fixed-window counter applies to every
  `/api/**` route, keyed by authenticated user id (falling back to IP for anonymous calls read
  from `getRemoteAddr()`, deliberately not `X-Forwarded-For`, to prevent trivial bypass).

## Layers, in one sentence each

- **Controller**: HTTP/WebSocket entry points — validates input, delegates, maps to a response.
- **Service**: transactional business logic, tenant-isolation checks, cross-module orchestration.
- **Domain**: JPA entities and their invariants.
- **Repository**: Spring Data JPA interfaces over PostgreSQL.
- **Infrastructure** (`common/`): CORS, WebSocket config, cache config, the rate-limit interceptor,
  and the global exception handler that turns thrown `ApiException`s into the standard error
  envelope documented in [API.md](API.md).
