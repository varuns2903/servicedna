# ServiceDNA Agent

An OpenTelemetry Collector that runs inside your environment — a Docker host or a Kubernetes
cluster — and forwards its telemetry to ServiceDNA:

- **No keys in your services.** Services send OTLP (gRPC `:4317`, HTTP `:4318`) or Zipkin
  (`:9411`) to the agent; the agent holds the environment's ingestion key.
- **Environment stamped on the way through.** Telemetry that doesn't say which environment it
  came from gets the agent's `SERVICEDNA_ENV`, so services register under the right one.
- **Traces and logs.** Both arrive on the same OTLP ports; logs keep their trace ids, so they show
  up on the requests that wrote them. Masking applies to log attributes and messages too.
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
existing Secret.

On Kubernetes the agent also tags every span and log with its pod, deployment, namespace and node, and a
workload that never set `OTEL_SERVICE_NAME` (so reports `unknown_service:…`) is registered under
its Deployment, StatefulSet or DaemonSet name. This needs read access to pods and workloads, which
the chart grants; turn it off with `--set kubernetesMetadata.enabled=false`.

Then, in your workloads:

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

## Service meshes

A mesh's sidecars can report every hop without touching application code. Point their tracing at
the agent:

- **Istio**: `meshConfig.extensionProviders` with an `opentelemetry` provider at
  `<agent>.<namespace>.svc.cluster.local:4317`, enabled with a `Telemetry` resource
- **Linkerd**: install `linkerd-jaeger` with its collector exporting OTLP to the agent, or set the
  proxy's trace collector to `<agent>:4317`
- **Envoy** (standalone): the `envoy.tracers.opentelemetry` tracer with a gRPC cluster at the agent

Sidecar spans carry the mesh's service names, so services register and the graph fills in from
mesh traffic alone; application instrumentation adds operations and inner spans on top.

## Coming next

The agent will also host the **Runner** (roadmap Phase 5), which executes Test Studio requests —
HTTP, gRPC and Kafka — inside your network.
