# ServiceDNA Runner

Sends Test Studio requests from inside your network. It connects **out** to ServiceDNA, claims
test runs for its environment, sends them to your services — HTTP, GraphQL, gRPC, Kafka — with
W3C trace context (`traceparent`) and the baggage `sdna.run=<id>,sdna.capture=1`, and reports the
entry call's result. The services' SDKs join the run's trace and capture each hop's bodies, so
ServiceDNA can show the request travelling through every service.

Credentials for your brokers and services stay here; ServiceDNA never holds them.

## Run it

```bash
docker run -d --name servicedna-runner \
  -e SERVICEDNA_URL=https://servicedna.example.com \
  -e SERVICEDNA_KEY=sdna_ik_... \
  -e SERVICEDNA_ENV=staging \
  -e KAFKA_BROKERS=kafka:9092 \
  servicedna-runner      # docker build -t servicedna-runner runner/
```

## Configuration

| Variable | | |
|---|---|---|
| `SERVICEDNA_URL` | required | ServiceDNA base URL |
| `SERVICEDNA_KEY` | required* | Organization ingestion key — the runner only sees that organization's runs |
| `RUNNER_SHARED_TOKEN` | required* | Instead of a key: the runner bundled with a self-hosted ServiceDNA (`docker compose --profile runner up`), serving every organization |
| `SERVICEDNA_ENV` | | Environment it serves; runs for other environments wait for their own runner |
| `RUNNER_NAME` | hostname | Shown on each run |
| `RUNNER_TARGETS` | | `service=http://host:port,…` — where services are, when their registered health-check URL isn't reachable from here |
| `RUNNER_GRPC_TARGETS` | | `service=host:port,…` for gRPC servers |
| `RUNNER_ALLOWED_SERVICES` / `RUNNER_ALLOWED_TOPICS` | everything | Comma-separated globs (`checkout-*`, `order.*`); anything else is refused |
| `KAFKA_BROKERS` | | Needed for messaging runs |
| `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_USERNAME`, `KAFKA_SASL_PASSWORD`, `KAFKA_TLS` | | `PLAIN`, `SCRAM-SHA-256` or `SCRAM-SHA-512` |

gRPC runs call unary methods discovered through **server reflection** — no `.proto` files needed on
the runner — with the JSON body converted to the request message.
