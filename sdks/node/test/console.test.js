'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { logs } = require('@opentelemetry/api-logs');
const { trace, context } = require('@opentelemetry/api');
const { LoggerProvider, SimpleLogRecordProcessor, InMemoryLogRecordExporter } = require('@opentelemetry/sdk-logs');
const { BasicTracerProvider } = require('@opentelemetry/sdk-trace-base');
const { AsyncLocalStorageContextManager } = require('@opentelemetry/context-async-hooks');
const { bridgeConsole } = require('../src/console');

const exporter = new InMemoryLogRecordExporter();
logs.setGlobalLoggerProvider(new LoggerProvider({ processors: [new SimpleLogRecordProcessor({ exporter })] }));
context.setGlobalContextManager(new AsyncLocalStorageContextManager().enable());
const tracer = new BasicTracerProvider().getTracer('test');

test('console calls become log records in the active trace, and still print', () => {
  const printed = [];
  const fake = {};
  for (const m of ['log', 'info', 'warn', 'error', 'debug']) fake[m] = (...args) => printed.push([m, ...args]);
  const restore = bridgeConsole(fake);

  const span = tracer.startSpan('POST /orders');
  context.with(trace.setSpan(context.active(), span), () => {
    fake.error('payment failed for %s', 'o-17', { code: 402 });
  });
  span.end();
  fake.info('started');
  restore();
  fake.log('after restore');

  assert.deepStrictEqual(printed.map((p) => p[0]), ['error', 'info', 'log']);
  const records = exporter.getFinishedLogRecords();
  assert.strictEqual(records.length, 2);
  assert.strictEqual(records[0].body, "payment failed for o-17 { code: 402 }");
  assert.strictEqual(records[0].severityText, 'ERROR');
  assert.strictEqual(records[0].spanContext.traceId, span.spanContext().traceId);
  assert.strictEqual(records[1].severityText, 'INFO');
  assert.strictEqual(records[1].spanContext, undefined);
});
