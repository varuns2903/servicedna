export type SnippetTarget = 'node' | 'python' | 'go' | 'java' | 'otel' | 'agent';

export const SNIPPET_TARGETS: { id: SnippetTarget; label: string }[] = [
  { id: 'node', label: 'Node.js' },
  { id: 'python', label: 'Python' },
  { id: 'go', label: 'Go' },
  { id: 'java', label: 'Spring Boot' },
  { id: 'otel', label: 'OpenTelemetry' },
  { id: 'agent', label: 'Agent (Docker / Kubernetes)' },
];

interface SnippetValues {
  url: string;
  key: string;
  environment: string;
}

export interface SnippetBlock {
  caption: string;
  code: string;
}

/** Copy-paste setup for each stack, with the organization's URL and key filled in. */
export function snippetFor(target: SnippetTarget, { url, key, environment }: SnippetValues): SnippetBlock[] {
  const env = `SERVICEDNA_URL=${url}\nSERVICEDNA_KEY=${key}\nSERVICEDNA_ENV=${environment}`;
  switch (target) {
    case 'node':
      return [
        { caption: 'Install', code: 'npm install @servicedna/node' },
        { caption: 'Run with these environment variables', code: `${env}\nnode -r @servicedna/node/register src/index.js` },
      ];
    case 'python':
      return [
        { caption: 'Install', code: 'pip install servicedna' },
        {
          caption: 'Prefix your usual command with servicedna-run',
          code: `${env}\nservicedna-run uvicorn app.main:app --host 0.0.0.0 --port 8000`,
        },
      ];
    case 'go':
      return [
        { caption: 'Install', code: 'go get github.com/varuns2903/servicedna/sdks/go' },
        {
          caption: 'Start it in main and wrap your router',
          code: `shutdown, err := servicedna.Start(ctx) // reads SERVICEDNA_* env vars
if err != nil {
	log.Fatal(err)
}
defer shutdown(context.Background())

http.ListenAndServe(":8080", sdnahttp.Handler(mux))`,
        },
        { caption: 'Run with these environment variables', code: env },
      ];
    case 'java':
      return [
        {
          caption: 'pom.xml',
          code: `<dependency>
  <groupId>io.github.varuns2903</groupId>
  <artifactId>servicedna-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>

<properties>
  <opentelemetry.version>1.65.0</opentelemetry.version>
</properties>`,
        },
        { caption: 'Run with these environment variables', code: env },
      ];
    case 'otel':
      return [
        {
          caption: 'Already instrumented with OpenTelemetry? Point the exporter at ServiceDNA',
          code: `OTEL_EXPORTER_OTLP_ENDPOINT=${url}/api/v1/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=${key}
OTEL_SERVICE_NAME=my-service
OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=${environment}`,
        },
      ];
    case 'agent':
      return [
        {
          caption: 'Kubernetes',
          code: `helm install servicedna-agent ./agent/helm/servicedna-agent \\
  --set servicedna.url=${url} \\
  --set servicedna.key=${key} \\
  --set servicedna.environment=${environment}`,
        },
        {
          caption: 'Docker',
          code: `cd agent && ${env.replace(/\n/g, ' ')} docker compose up -d`,
        },
        {
          caption: 'Then point services at the agent — no key needed',
          code: `OTEL_EXPORTER_OTLP_ENDPOINT=http://<agent>:4318
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_SERVICE_NAME=my-service`,
        },
      ];
  }
}
