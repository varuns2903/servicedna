# ServiceDNA Helm chart

Runs ServiceDNA on Kubernetes: the backend, the web app and (optionally) the Test Studio runner,
behind one Ingress host. Postgres, Redis, Kafka, Tempo and Loki come bundled for evaluation and
small teams; for production, point ServiceDNA at managed services instead.

Telemetry from your services reaches ServiceDNA through the same host (`/api/v1/otlp`), or through
the [agent chart](../../../agent/helm/servicedna-agent) in each cluster you monitor.

## Install

1. **Build and push the images** to a registry your cluster can pull from:

   ```bash
   REGISTRY=registry.example.com/servicedna TAG=1.0.0
   for image in backend frontend runner; do
     docker build -t $REGISTRY/servicedna-$image:$TAG ./$image
     docker push $REGISTRY/servicedna-$image:$TAG
   done
   ```

   The frontend image uses same-origin paths (`/api/v1`, `/oauth2/…`), so one build works at any
   address.

2. **Install**, with the address people's browsers will use:

   ```bash
   helm install servicedna deploy/helm/servicedna -n servicedna --create-namespace \
     --set url=https://servicedna.example.com \
     --set image.registry=$REGISTRY \
     --set backend.image.tag=$TAG,frontend.image.tag=$TAG,runner.image.tag=$TAG \
     --set ingress.className=nginx \
     --set-string ingress.annotations."cert-manager\.io/cluster-issuer"=letsencrypt
   ```

   The backend migrates the database on first start. Then open the URL and **sign up as the
   admin straight away**: sign-up is invite-only by default (`signup.mode`), so after the first
   account only people you invite, or emails in `signup.domains`, can create one.

The Ingress sends `/api`, `/oauth2`, `/login/oauth2` and `/ws` to the backend and everything else
to the web app. Spring's `/actuator` endpoints aren't exposed. TLS is on by default (`ingress.tls`),
using the certificate in `servicedna-tls` — cert-manager fills it in when the Ingress is annotated
as above.

## Secrets

No secret ever goes in `values.yaml`. Either:

- **Let the chart generate them** (the default): `jwt-secret`, `database-password` and
  `runner-token` are generated once into `<release>-servicedna-secrets`, reused on every upgrade,
  and kept when the release is uninstalled, so a reinstall can still open the database. Back this
  Secret up along with the database.
- **Bring your own** (recommended for production — External Secrets, Sealed Secrets, Vault):
  set `secrets.existingSecret` to a Secret with those three keys, plus any optional ones you expose
  with `backend.secretEnv`.

Optional settings that are secret go in the same Secret and are mapped to environment variables:

```yaml
smtp: {host: smtp.example.com, port: 587, username: servicedna, from: servicedna@example.com}
backend:
  env:
    GITHUB_APP_ID: "123456"
    GITHUB_APP_SLUG: servicedna-acme
    GITHUB_APP_CLIENT_ID: Iv1.abc
  secretEnv:
    SMTP_PASSWORD: smtp-password
    GITHUB_APP_PRIVATE_KEY: github-app-private-key
    GITHUB_APP_WEBHOOK_SECRET: github-app-webhook-secret
    GITHUB_APP_CLIENT_SECRET: github-app-client-secret
```

Without SMTP, ServiceDNA can't send verification emails, and new accounts can't sign in again
once their first session ends. Set `smtp.*` before inviting people.

## Production checklist

| | |
|---|---|
| **Datastores** | Turn off the bundled ones and use managed services: `postgresql.enabled=false` + `postgresql.host` (Postgres 15+), `redis.enabled=false` + `redis.host`, `kafka.enabled=false` + `kafka.bootstrapServers`. For traces and logs, run Tempo and Loki with multi-tenancy on (each organization is a tenant) and set `tempo.queryUrl`/`tempo.otlpUrl` and `loki.url` |
| **Secrets** | `secrets.existingSecret`, managed outside the release |
| **TLS** | Ingress TLS (above). ServiceDNA trusts `X-Forwarded-*` from the Ingress for its public https URL |
| **Email** | `smtp.*` and `SMTP_PASSWORD` |
| **Sign-up** | `signup.mode: invite-only` (default) — invitations and `signup.domains` only |
| **Backups** | Your database service's backups, or `backup.enabled=true` (below) |
| **Network** | `networkPolicy.enabled` (default) lets only the backend reach the bundled datastores — it needs a CNI that enforces NetworkPolicy (Calico, Cilium, …) |
| **Resources** | The backend's JVM uses 75% of its memory limit (`backend.resources`) |

Every pod runs as a non-root user with a read-only root filesystem (Kafka excepted), no Linux
capabilities, the runtime's default seccomp profile, and no service-account token.

## Backups and restore

`backup.enabled=true` runs `pg_dump` on a schedule (`backup.schedule`, nightly by default) into a
persistent volume, keeping `backup.keepDays` days. Copy the dumps off the cluster as well — a
volume in the same cluster isn't a backup on its own.

To restore into a fresh database:

```bash
kubectl -n servicedna scale deploy/servicedna-backend --replicas=0
kubectl -n servicedna cp <dump-file> <a pod with psql>:/tmp/servicedna.dump
pg_restore --clean --if-exists --no-owner -d servicedna /tmp/servicedna.dump   # in that pod
kubectl -n servicedna scale deploy/servicedna-backend --replicas=1
```

Restore with the same `jwt-secret` (or everyone signs in again) and the same `database-password`.

## Upgrades

```bash
helm upgrade servicedna deploy/helm/servicedna -n servicedna --reuse-values \
  --set backend.image.tag=$NEW,frontend.image.tag=$NEW,runner.image.tag=$NEW
```

The backend applies database migrations (Flyway) when it starts; the old version keeps serving until
the new one is ready. Migrations only move forward, so **back up before upgrading**: `helm rollback`
returns the application to the previous version, but restoring the backup is what returns the
database. Read the [changelog](../../../CHANGELOG.md) for each version you skip.

## Scaling

`backend.replicas` can be raised: the call graph pairs through Redis and GitHub polling claims its
work in Postgres, so replicas don't duplicate either. Each replica runs its own health prober. The
web app is static and scales freely.

## Values

See [`values.yaml`](values.yaml); every setting is commented. Plain backend settings from the
[environment variables](../../../README.md#-environment-variables) go in `backend.env`.
