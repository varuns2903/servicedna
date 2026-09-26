# ServiceDNA GitHub Action

Keeps ServiceDNA in step with a repository, and tests pull requests against real services:

- **On pushes to the default branch:** `sdna scan` — applies `servicedna.yaml` (owner, tier, SLO,
  alert rules) and the repository's OpenAPI / proto / AsyncAPI specs and configured dependencies.
- **On pull requests:** `sdna test run flows/` — sends each flow through a ServiceDNA runner and
  checks every hop. The results go to the job summary and one PR comment (updated on each push),
  each case linking to its trace; the check fails if a case fails.

```yaml
# .github/workflows/servicedna.yml
name: ServiceDNA
on:
  push:
    branches: [main]
  pull_request:
permissions:
  contents: read
  pull-requests: write   # for the results comment
jobs:
  servicedna:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: varuns2903/servicedna/integrations/github-action@main
        with:
          url: https://servicedna.example.com
          token: ${{ secrets.SERVICEDNA_TOKEN }}   # an API token: Settings → Account → API Tokens
          org: Acme
          environment: staging                     # where PR flows run
```

| Input | Default | |
|---|---|---|
| `url`, `token`, `org` | — | Where ServiceDNA is, an API token, and the organization (name or id) |
| `app-url` | `url` | The web app, for links to traces |
| `environment` | the flow files' | Environment the flows run in |
| `flows` | `flows` | Flow files or directory |
| `scan` / `test` | `auto` | `auto` (as above), `true` or `false` |
| `comment` | `true` | Post the results on the PR |
| `working-directory` | `.` | The service's directory, for monorepos |
| `cli-version` | `main` | Git ref of the `sdna` CLI to install |

ServiceDNA must be reachable from the runner, and the test environment needs a
[runner](../../runner) that can reach its services. The logic lives in [`run.sh`](run.sh), which
runs anywhere with the same environment variables — handy for other CI systems.
