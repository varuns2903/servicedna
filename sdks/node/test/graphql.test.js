'use strict';

// As the SDK registers them: HTTP and GraphQL instrumentation, with the operation processor.
const { NodeTracerProvider, SimpleSpanProcessor, InMemorySpanExporter } = require('@opentelemetry/sdk-trace-node');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { GraphQLInstrumentation } = require('@opentelemetry/instrumentation-graphql');
const { GraphqlOperationProcessor } = require('../src/graphql');

const exporter = new InMemorySpanExporter();
const provider = new NodeTracerProvider({ spanProcessors: [new GraphqlOperationProcessor(), new SimpleSpanProcessor(exporter)] });
provider.register();
registerInstrumentations({ instrumentations: [new HttpInstrumentation(), new GraphQLInstrumentation()] });

const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const { graphql, buildSchema } = require('graphql');

const schema = buildSchema('type Product { id: ID! } type Order { id: ID! } type Query { products: [Product!]! orders: [Order!]! }');
const root = { products: () => [{ id: 'p-1' }], orders: () => [{ id: 'o-1' }] };

async function serverSpanFor(query) {
  exporter.reset();
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', async () => {
      const result = await graphql({ schema, source: JSON.parse(body).query, rootValue: root });
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify(result));
    });
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const { port } = server.address();
  await fetch(`http://127.0.0.1:${port}/graphql`, { method: 'POST', body: JSON.stringify({ query }) });
  await new Promise((r) => setTimeout(r, 50));
  server.close();
  return exporter.getFinishedSpans().find((s) => s.kind === 1 /* SERVER */);
}

test('a named operation names the request', async () => {
  const span = await serverSpanFor('query ListProducts { products { id } }');
  assert.strictEqual(span.attributes['graphql.operation.type'], 'query');
  assert.strictEqual(span.attributes['graphql.operation.name'], 'ListProducts');
});

test('an anonymous operation is named after its top-level fields', async () => {
  const span = await serverSpanFor('{ products { id } orders { id } }');
  assert.strictEqual(span.attributes['graphql.operation.type'], 'query');
  assert.strictEqual(span.attributes['graphql.operation.name'], 'products,orders');
});

test('requests that are not GraphQL are left alone', async () => {
  exporter.reset();
  const server = http.createServer((req, res) => res.end('ok'));
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  await fetch(`http://127.0.0.1:${server.address().port}/health`);
  await new Promise((r) => setTimeout(r, 50));
  server.close();
  const span = exporter.getFinishedSpans().find((s) => s.kind === 1);
  assert.strictEqual(span.attributes['graphql.operation.name'], undefined);
});
