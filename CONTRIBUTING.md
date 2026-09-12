# Contributing

Thanks for considering a contribution to ServiceDNA! This project follows a fairly standard
fork-and-PR workflow.

## Getting Set Up

Follow the [Quick Start](README.md#-quick-start) in the README to get the backend and frontend
running locally. You'll need Java 21, Node 20+, and Docker for the infrastructure (Postgres,
Redis, Kafka, Zipkin).

## Development Workflow

1. Fork the repo and create a branch off `main`: `git checkout -b feat/short-description`.
2. Make your change. Keep it scoped — a focused PR is much easier to review than one that mixes a
   bug fix with a refactor.
3. Run the checks below before opening a PR.
4. Open a PR against `main` with a clear description of *why*, not just *what* — link any related
   issue.

## Commit Messages

Use conventional, meaningful commit messages:
- `feat(module): add new functionality`
- `fix(module): patch bug`
- `test(module): add tests`
- `refactor(module): change code structure without changing behavior`
- `docs(module): documentation only`

## Running Checks Locally

**Backend** (from `backend/`):
```bash
./mvnw compile                 # compile
./mvnw test                    # full test suite (JUnit + Spring Boot Test)
```

**Frontend** (from `frontend/`):
```bash
npx tsc -b --noEmit             # type-check
npm run test:e2e                # Playwright E2E suite (requires the backend + frontend running)
```

Flyway migrations run automatically on backend startup — if your change touches the schema, add a
new `V{n}__description.sql` file under `backend/src/main/resources/db/migration/` rather than
editing an existing one; migrations are append-only.

## Definition of Done

1. The change compiles and the relevant test suite passes.
2. New behavior has test coverage — a bug fix should include a regression test; a new endpoint or
   UI flow should be exercised at least once (an automated test where practical, or a documented
   manual verification in the PR description otherwise).
3. Security implications have been considered — does this change touch auth, tenant isolation, or
   anything that crosses a trust boundary? Call it out explicitly in the PR if so.
4. User-facing changes are reflected in [API.md](API.md), [README.md](README.md), or the relevant
   doc — whichever actually describes the thing you changed.
5. No secrets, API keys, or `.env` files are included in the diff.

## Code Style

- Backend: standard Java conventions, four-space indentation, no wildcard imports. Favor
  constructor injection over field injection.
- Frontend: TypeScript strict mode is on — don't work around it with `any`. Components follow the
  existing feature-module layout under `frontend/src/features/`.
- Comments should explain *why*, not *what* — a non-obvious constraint, a workaround, an invariant
  that isn't visible from the code itself. Skip comments that just restate what a well-named
  function already says.

## Reporting Bugs

Open a GitHub issue with: what you did, what you expected, what actually happened, and your
environment (OS, Java/Node versions, whether you're running via Docker Compose or locally). A
minimal reproduction is worth more than a long description.

## Reporting Security Issues

Please don't open a public issue for a security vulnerability — see [SECURITY.md](SECURITY.md) for
how to report it privately.
