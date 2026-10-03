# sdna — ServiceDNA CLI

```bash
sdna login --url https://servicedna.example.com   # password prompted (or SDNA_PASSWORD)
sdna status                                       # services, health, environments, open incidents
sdna init --env dev                               # connect the project in this directory
```

| Command | |
|---|---|
| `sdna login [--url URL] [--email EMAIL]` | Sign in; picks your first organization |
| `sdna github create-app --url URL [--org ORG] [--public]` | Create the ServiceDNA GitHub App on GitHub (opens your browser) and write its `GITHUB_APP_*` settings to `.env.github-app`. With `--poll --app-url URL` instead of `--url`, the app has no webhooks and ServiceDNA polls GitHub — for a ServiceDNA GitHub can't reach |
| `sdna login --token sdna_pat_…` | Sign in with an API token (Settings → Account) — for SSO accounts |
| `sdna orgs` / `sdna use <org>` | List organizations / choose the one other commands use |
| `sdna status` | Services with status, environment, language/version and last telemetry; open incidents |
| `sdna keys list` / `sdna keys create <name>` | Ingestion keys (a created key is shown once) |
| `sdna scan [--service NAME] [--env ENV] [--dry-run] [--dir DIR]` | Apply the repository's `servicedna.yaml` (below) and read its OpenAPI, `.proto` and AsyncAPI specs into the service's API catalog, and declare dependencies on the registered services its configuration names (`.env`, Spring `application.*`, k8s/helm manifests, and each service in a docker-compose file). Works on private repos and in CI — nothing leaves your machine but the results |
| `sdna test run <flow.yaml\|dir>... [--env ENV] [--timeout 5m]` / `--collection NAME` | Run test flows (YAML in the repo) or a saved collection as suites; prints each case and failed check; exits 1 on any failure — drop it into CI |
| `sdna init [--env ENV] [--no-install] [--dir DIR]` | Detect the stack (Node, Python, Go, Spring Boot), create an ingestion key, add the SDK, write `.env.servicedna` (and gitignore it), and print how to start the service |

Settings live in `~/.config/servicedna/config.json` (owner-only; override with `SDNA_CONFIG`).
The session refreshes itself; `sdna login` again if it's revoked.

### In CI

No login needed: create an API token (Settings → Account → API Tokens) and set

```bash
SDNA_URL=https://servicedna.example.com
SDNA_TOKEN=sdna_pat_…        # a secret in your CI
SDNA_ORG=Acme                # organization name or id
sdna scan && sdna test run flows/
```

Settings from the environment are never written to the config file. `sdna test run --report
results.md` also writes the results as Markdown (set `SDNA_APP_URL` to link each case to its trace).
On GitHub, the [ServiceDNA Action](../integrations/github-action) does all of this, and comments on
pull requests.

### servicedna.yaml

The catalog entry for a repository's service — what traffic can't tell ServiceDNA. `sdna init`
writes one; `sdna scan` applies it (run it in CI on every push to keep ServiceDNA in step).

```yaml
service: order-service
description: "Places orders"
owner: team-checkout
tier: critical            # critical | high | medium | low
slo: 99.9
health: http://order-service:4004/health   # the URL ServiceDNA probes
repository: https://github.com/acme/order-service
dependencies: [payment-service]            # optional: calls seen in traffic are added anyway
alerts:                                    # replaces the rules this file set before
  - condition: STATUS_DOWN
    open_incident: CRITICAL
  - condition: LATENCY_ABOVE               # also ERROR_RATE_ABOVE, CONSECUTIVE_FAILURES, STATUS_DEGRADED
    threshold: 800                         # ms (percent, or a count, for the others)
    window_minutes: 5
    webhook: https://hooks.slack.com/…
    integration: SLACK
```

Unknown keys and invalid values are errors, so typos don't pass silently. Rules the file manages
are marked in the UI; rules made there are left alone.

### Flow files

```yaml
name: Checkout
environment: staging          # optional; --env overrides
cases:
  - name: an order is paid and confirmed
    request:                  # protocol defaults to HTTP (also GRPC, GRAPHQL, MESSAGING)
      service: api-gateway
      method: POST
      path: /api/orders
      body: {userId: u-1, items: [{productId: p-2}]}
    expect:
      - {entry: true, status: 201, latencyMs: {lt: 2000}}
      - {service: order-service, operation: POST /orders, response: {status: CONFIRMED}}
      - {service: payment-service, operation: Charge, exists: true}
      - {service: notification-service, request: {total: 59}}
```

Runs are in test mode unless a request sets `testMode: false`.

## Install

Download a binary from the [releases](https://github.com/varuns2903/servicedna/releases) (tags
`cli-v*`), or build it:

```bash
go install github.com/varuns2903/servicedna/cli@latest   # installs as `cli`; rename to sdna
# or
cd cli && go build -o sdna .
```

Password login only: accounts that sign in with GitHub or SSO need a password set first (personal
access tokens are on the roadmap).
