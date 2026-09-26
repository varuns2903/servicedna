# sdna — ServiceDNA CLI

```bash
sdna login --url https://servicedna.example.com   # password prompted (or SDNA_PASSWORD)
sdna status                                       # services, health, environments, open incidents
sdna init --env dev                               # connect the project in this directory
```

| Command | |
|---|---|
| `sdna login [--url URL] [--email EMAIL]` | Sign in; picks your first organization |
| `sdna orgs` / `sdna use <org>` | List organizations / choose the one other commands use |
| `sdna status` | Services with status, environment, language/version and last telemetry; open incidents |
| `sdna keys list` / `sdna keys create <name>` | Ingestion keys (a created key is shown once) |
| `sdna scan [--service NAME] [--env ENV] [--dry-run] [--dir DIR]` | Read the repository's OpenAPI, `.proto` and AsyncAPI specs into the service's API catalog, and declare dependencies on the registered services its configuration names (`.env`, Spring `application.*`, k8s/helm manifests, and each service in a docker-compose file). Works on private repos and in CI — nothing leaves your machine but the results |
| `sdna init [--env ENV] [--no-install] [--dir DIR]` | Detect the stack (Node, Python, Go, Spring Boot), create an ingestion key, add the SDK, write `.env.servicedna` (and gitignore it), and print how to start the service |

Settings live in `~/.config/servicedna/config.json` (owner-only; override with `SDNA_CONFIG`).
The session refreshes itself; `sdna login` again if it's revoked.

Test runs and trace lookups (`sdna test run`, `sdna trace`) arrive with the roadmap's Test Studio.

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
