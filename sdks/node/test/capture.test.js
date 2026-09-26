'use strict';

// Instrumentation must be registered before node:http is used by the test server/client.
const { NodeTracerProvider, SimpleSpanProcessor, InMemorySpanExporter } = require('@opentelemetry/sdk-trace-node');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { W3CTraceContextPropagator, W3CBaggagePropagator, CompositePropagator } = require('@opentelemetry/core');
const exporter = new InMemorySpanExporter();
const provider = new NodeTracerProvider({ spanProcessors: [new SimpleSpanProcessor(exporter)] });
provider.register({
  propagator: new CompositePropagator({ propagators: [new W3CTraceContextPropagator(), new W3CBaggagePropagator()] }),
});
const { httpHooks, redact } = require('../src/capture');
registerInstrumentations({ instrumentations: [new HttpInstrumentation({ requestHook: httpHooks.requestHook, responseHook: httpHooks.responseHook })] });

const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');

function server() {
  return new Promise((resolve) => {
    const s = http.createServer((req, res) => {
      let body = '';
      req.on('data', (c) => (body += c));
      req.on('end', () => {
        res.setHeader('content-type', 'application/json');
        res.end(JSON.stringify({ received: JSON.parse(body || '{}'), token: 'abc' }));
      });
    });
    s.listen(0, '127.0.0.1', () => resolve(s));
  });
}

function send(port, headers, payload) {
  return new Promise((resolve, reject) => {
    const req = http.request({ port, host: '127.0.0.1', method: 'POST', path: '/orders', headers: { 'content-type': 'application/json', ...headers } }, (res) => {
      res.resume();
      res.on('end', resolve);
    });
    req.on('error', reject);
    req.end(JSON.stringify(payload));
  });
}

const serverSpan = () => exporter.getFinishedSpans().find((s) => s.kind === 1 /* SERVER */);

test('records bodies of a capture run, masking credentials', async () => {
  exporter.reset();
  const s = await server();
  await send(s.address().port, {
    traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
    baggage: 'sdna.run=run_1,sdna.capture=1',
  }, { userId: 'u-1', password: 'hunter2' });
  await new Promise((r) => setTimeout(r, 50));
  s.close();

  const span = serverSpan();
  assert.ok(span, 'server span exported');
  assert.deepStrictEqual(JSON.parse(span.attributes['sdna.request.body']), { userId: 'u-1', password: '[masked]' });
  assert.deepStrictEqual(JSON.parse(span.attributes['sdna.response.body']), { received: { userId: 'u-1', password: '[masked]' }, token: '[masked]' });
  assert.strictEqual(span.attributes['sdna.captured'], true);
});

test('leaves ordinary requests alone', async () => {
  exporter.reset();
  const s = await server();
  await send(s.address().port, { traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4737-00f067aa0ba902b7-01' }, { userId: 'u-2' });
  await new Promise((r) => setTimeout(r, 50));
  s.close();

  const span = serverSpan();
  assert.ok(span);
  assert.strictEqual(span.attributes['sdna.request.body'], undefined);
  assert.strictEqual(span.attributes['sdna.response.body'], undefined);
});

test('redact truncates and leaves non-JSON text alone', () => {
  assert.strictEqual(redact('plain text'), 'plain text');
  assert.match(redact('x'.repeat(20000)), /…\[truncated\]$/);
});
