# ServiceDNA Agent

An OpenTelemetry Collector that runs inside your environment — a Docker host or a Kubernetes
cluster — and forwards its telemetry to ServiceDNA:

- **No keys in your services.** Services send OTLP (gRPC `:4317`, HTTP `:4318`) or Zipkin
  (`:9411`) to the agent; the agent holds the environment's ingestion key.
- **Environment stamped on the way through.** Telemetry that doesn't say which environment it
  came from gets the agent's `SERVICEDNA_ENV`, so services register under the right one.
- **Sensitive data masked inside your network.** Attributes named like passwords, tokens, API keys,
  cookies or sessions are dropped, and card numbers in any value become `[card]`, before anything
  leaves. Extend the patterns in [`collector.yaml`](helm/servicedna-agent/files/collector.yaml).
- **Outbound only.** The agent connects out to ServiceDNA; nothing connects in.

Services using a ServiceDNA SDK can keep talking to ServiceDNA directly; the agent is for
everything else — plain OpenTelemetry, Zipkin, service meshes, eBPF instrumentation.

## Kubernetes

```bash
helm install servicedna-agent ./agent/helm/servicedna-agent \
  --set servicedna.url=https://servicedna.example.com \
  --set servicedna.key=sdna_ik_... \
  --set servicedna.environment=prod
```

Use `--set servicedna.existingSecret.name=<secret>` (and `.key=<field>`) to take the key from an
existing Secret. Then, in your workloads:

```yaml
env:
  - {name: OTEL_EXPORTER_OTLP_ENDPOINT, value: "http://servicedna-agent-servicedna-agent.<namespace>:4318"}
  - {name: OTEL_EXPORTER_OTLP_PROTOCOL, value: http/protobuf}
  - {name: OTEL_SERVICE_NAME, value: checkout}
```

## Docker

```bash
cd agent
SERVICEDNA_URL=https://servicedna.example.com SERVICEDNA_KEY=sdna_ik_... SERVICEDNA_ENV=prod \
  docker compose up -d
```

Services then send to `http://<agent-host>:4318`.

## Coming next

The agent will also host the **Runner** (roadmap Phase 5), which executes Test Studio requests —
HTTP, gRPC and Kafka — inside your network.
