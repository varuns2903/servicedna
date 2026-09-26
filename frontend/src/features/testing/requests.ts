import type { Assertion, CatalogOperation, Hop, TestProtocol, TestRequest, TestRun } from '@/api/testing.api';

type Schema = {
  type?: string | string[];
  properties?: Record<string, Schema>;
  items?: Schema;
  example?: unknown;
  examples?: unknown[];
  default?: unknown;
  enum?: unknown[];
  format?: string;
  oneOf?: Schema[];
  anyOf?: Schema[];
  allOf?: Schema[];
};

/** A sample value for a JSON schema: its example or default if it has one, else a placeholder of its type. */
export function exampleOf(schema: Schema | undefined, depth = 0): unknown {
  if (!schema || depth > 8) return null;
  if (schema.example !== undefined) return schema.example;
  if (schema.examples?.length) return schema.examples[0];
  if (schema.default !== undefined) return schema.default;
  if (schema.enum?.length) return schema.enum[0];
  const variant = schema.oneOf?.[0] ?? schema.anyOf?.[0];
  if (variant) return exampleOf(variant, depth + 1);
  if (schema.allOf?.length) {
    return Object.assign({}, ...schema.allOf.map((s) => exampleOf(s, depth + 1)).filter((v) => typeof v === 'object'));
  }
  const type = Array.isArray(schema.type) ? schema.type.find((t) => t !== 'null') : schema.type;
  switch (type ?? (schema.properties ? 'object' : undefined)) {
    case 'object':
      return Object.fromEntries(Object.entries(schema.properties ?? {}).map(([k, v]) => [k, exampleOf(v, depth + 1)]));
    case 'array':
      return schema.items ? [exampleOf(schema.items, depth + 1)] : [];
    case 'integer':
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'string':
      return schema.format === 'date-time' ? new Date(0).toISOString() : schema.format === 'uuid' ? '00000000-0000-0000-0000-000000000000' : '';
    default:
      return null;
  }
}

/** A body template from an operation's request schema, or null when it has none. */
export function templateFor(operation: CatalogOperation): string | null {
  if (!operation.requestSchema) return null;
  try {
    return JSON.stringify(exampleOf(JSON.parse(operation.requestSchema)), null, 2);
  } catch {
    return null;
  }
}

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];

/** The request fields an operation from the catalog fills in. */
export function requestFor(operation: CatalogOperation): Partial<TestRequest> {
  const name = operation.name.trim();
  switch (operation.protocol) {
    case 'GRPC':
      return { protocol: 'GRPC', grpcMethod: name };
    case 'MESSAGING':
      // Operations seen in traffic are named "<topic> publish", "process <topic>" or "consume <topic>".
      return { protocol: 'MESSAGING', topic: name.replace(/^(publish|process|receive|send|consume)\s+|\s+(publish|process|receive|send|create|deliver)$/gi, '') };
    case 'GRAPHQL':
      return { protocol: 'GRAPHQL', method: 'POST', path: '/graphql' };
    default: {
      const [first, ...rest] = name.split(/\s+/);
      if (HTTP_METHODS.includes(first.toUpperCase()) && rest.length) {
        return { protocol: 'HTTP', method: first.toUpperCase(), path: fillPathParams(rest.join(' ')) };
      }
      return { protocol: 'HTTP', method: 'GET', path: name.startsWith('/') ? name : `/${name}` };
    }
  }
}

/** Route templates ("/orders/{id}", "/orders/:id") need a concrete value; mark it so it's obvious. */
function fillPathParams(path: string) {
  return path.replace(/\{([^}]+)\}|:([A-Za-z_]\w*)/g, (_, a, b) => `{${a ?? b}}`);
}

export function protocolsOf(operations: CatalogOperation[] | undefined): TestProtocol[] {
  const all: TestProtocol[] = ['HTTP', 'GRPC', 'GRAPHQL', 'MESSAGING'];
  return all.filter((p) => operations?.some((o) => o.protocol === p));
}

/** "Key: value" lines to headers; blank lines and lines without a colon are ignored. */
export function parseHeaders(text: string): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const line of text.split('\n')) {
    const i = line.indexOf(':');
    if (i > 0) headers[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return headers;
}

export function formatHeaders(headers: Record<string, string> | undefined | null) {
  return Object.entries(headers ?? {})
    .map(([k, v]) => `${k}: ${v}`)
    .join('\n');
}

export function prettyJson(text: string | null | undefined) {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

export function hopAssertion(hop: Hop): Assertion {
  const assertion: Assertion = { target: { service: hop.service, operation: hop.operation }, exists: true };
  if (hop.httpStatus != null) assertion.status = hop.httpStatus;
  return assertion;
}

/**
 * Assertions describing what a run did — the entry status and every service it reached — as a
 * starting point to edit: a passing run becomes a regression test in one click.
 */
export function assertionsFromRun(run: TestRun): Assertion[] {
  const assertions: Assertion[] = [];
  if (run.result?.status != null && run.protocol !== 'MESSAGING') {
    assertions.push({ target: { entry: true }, status: run.result.status });
  }
  const seen = new Set<string>();
  for (const hop of run.hops ?? []) {
    const key = `${hop.service}|${hop.operation}`;
    if (seen.has(key)) continue;
    seen.add(key);
    assertions.push(hopAssertion(hop));
  }
  return assertions;
}

/** The composer's request as it is sent and saved: empty fields dropped, only what the protocol uses. */
export function cleanRequest(request: TestRequest): TestRequest {
  const base: TestRequest = {
    protocol: request.protocol,
    environment: request.environment || null,
    headers: request.headers && Object.keys(request.headers).length ? request.headers : undefined,
    body: request.body || null,
    testMode: request.testMode,
  };
  switch (request.protocol) {
    case 'MESSAGING':
      return { ...base, topic: request.topic, key: request.key || null };
    case 'GRPC':
      return { ...base, serviceId: request.serviceId, serviceName: request.serviceName, grpcMethod: request.grpcMethod };
    default:
      return { ...base, serviceId: request.serviceId, serviceName: request.serviceName, method: request.method, path: request.path };
  }
}
