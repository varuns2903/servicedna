'use strict';

// Instrumentations first, as the SDK registers them: OpenTelemetry's gRPC, then capture.
const { NodeTracerProvider, SimpleSpanProcessor, InMemorySpanExporter } = require('@opentelemetry/sdk-trace-node');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');
const { GrpcInstrumentation } = require('@opentelemetry/instrumentation-grpc');
const { W3CTraceContextPropagator, W3CBaggagePropagator, CompositePropagator } = require('@opentelemetry/core');
const { GrpcCaptureInstrumentation } = require('../src/grpc');

const exporter = new InMemorySpanExporter();
const provider = new NodeTracerProvider({ spanProcessors: [new SimpleSpanProcessor(exporter)] });
provider.register({ propagator: new CompositePropagator({ propagators: [new W3CTraceContextPropagator(), new W3CBaggagePropagator()] }) });
registerInstrumentations({ instrumentations: [new GrpcInstrumentation(), new GrpcCaptureInstrumentation()] });

const test = require('node:test');
const assert = require('node:assert');
const grpc = require('@grpc/grpc-js');

// A service with JSON messages, so no .proto is needed.
const json = { serialize: (o) => Buffer.from(JSON.stringify(o)), deserialize: (b) => JSON.parse(b.toString()) };
const service = {
  Charge: {
    path: '/shop.Payments/Charge',
    requestStream: false,
    responseStream: false,
    requestSerialize: json.serialize,
    requestDeserialize: json.deserialize,
    responseSerialize: json.serialize,
    responseDeserialize: json.deserialize,
  },
};

async function start() {
  const server = new grpc.Server();
  server.addService(service, {
    Charge: (call, callback) => {
      if (call.request.amount > 1000) return callback({ code: grpc.status.FAILED_PRECONDITION, message: 'declined' });
      callback(null, { status: 'APPROVED', amount: call.request.amount, apiKey: 'k' });
    },
  });
  const port = await new Promise((resolve, reject) =>
    server.bindAsync('127.0.0.1:0', grpc.ServerCredentials.createInsecure(), (err, p) => (err ? reject(err) : resolve(p))),
  );
  const Client = grpc.makeGenericClientConstructor(service, 'Payments');
  return { server, client: new Client(`127.0.0.1:${port}`, grpc.credentials.createInsecure()) };
}

function charge(client, request, baggage) {
  const metadata = new grpc.Metadata();
  metadata.set('traceparent', '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01');
  if (baggage) metadata.set('baggage', baggage);
  return new Promise((resolve) => client.Charge(request, metadata, (err, res) => resolve({ err, res })));
}

async function serverSpan(run) {
  exporter.reset();
  const { server, client } = await start();
  await run(client);
  await new Promise((r) => setTimeout(r, 50));
  client.close();
  server.forceShutdown();
  return exporter.getFinishedSpans().find((s) => s.kind === 1 /* SERVER */);
}

test('a capture run records the messages on the gRPC server span', async () => {
  const span = await serverSpan((c) => charge(c, { orderId: 'o-1', amount: 59, password: 'x' }, 'sdna.capture=1'));
  assert.deepStrictEqual(JSON.parse(span.attributes['sdna.request.body']), { orderId: 'o-1', amount: 59, password: '[masked]' });
  assert.deepStrictEqual(JSON.parse(span.attributes['sdna.response.body']), { status: 'APPROVED', amount: 59, apiKey: '[masked]' });
  assert.strictEqual(span.attributes['sdna.captured'], true);
});

test('ordinary calls are left alone', async () => {
  const span = await serverSpan((c) => charge(c, { orderId: 'o-2', amount: 5 }));
  assert.strictEqual(span.attributes['sdna.request.body'], undefined);
});

test('with capture on error, only failed calls carry their request', async (t) => {
  process.env.SERVICEDNA_CAPTURE_ON_ERROR = 'true';
  t.after(() => delete process.env.SERVICEDNA_CAPTURE_ON_ERROR);
  const ok = await serverSpan((c) => charge(c, { orderId: 'o-3', amount: 5 }));
  assert.strictEqual(ok.attributes['sdna.request.body'], undefined);
  const failed = await serverSpan(async (c) => {
    const { err } = await charge(c, { orderId: 'o-4', amount: 5000 });
    assert.strictEqual(err.code, grpc.status.FAILED_PRECONDITION);
  });
  assert.deepStrictEqual(JSON.parse(failed.attributes['sdna.request.body']), { orderId: 'o-4', amount: 5000 });
  assert.strictEqual(failed.attributes['sdna.response.body'], undefined);
  assert.strictEqual(failed.attributes['sdna.captured_on_error'], true);
});
