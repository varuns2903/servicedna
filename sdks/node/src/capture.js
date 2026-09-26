'use strict';

const http = require('node:http');
const { context, propagation, trace } = require('@opentelemetry/api');

/**
 * Request/response body capture for ServiceDNA test runs. Only requests carrying the baggage entry
 * `sdna.capture=1` (set by the ServiceDNA runner) are captured; everything else is untouched.
 * Captured bodies are masked (credential-like JSON fields) and truncated before they're recorded
 * as span attributes.
 */
const MAX_BYTES = Number(process.env.SERVICEDNA_CAPTURE_MAX_BYTES ?? 16384);
const SENSITIVE = /pass(word|wd)?|secret|token|api[-_.]?key|authorization|cookie|session|card|cvv|ssn/i;

function captureRequested(ctx = context.active()) {
  return propagation.getBaggage(ctx)?.getEntry('sdna.capture')?.value === '1';
}

/** Masks credential-like fields in JSON; other text is kept as is. Always truncates. */
function redact(text) {
  let out = text;
  try {
    const parsed = JSON.parse(text);
    out = JSON.stringify(parsed, (key, value) => (key && SENSITIVE.test(key) ? '[masked]' : value));
  } catch {
    // not JSON
  }
  return out.length > MAX_BYTES ? `${out.slice(0, MAX_BYTES)}…[truncated]` : out;
}

/** Collects chunks without changing how the stream behaves: watches emit('data') instead of listening. */
function watchData(emitter, onChunk) {
  const emit = emitter.emit;
  emitter.emit = function (event, chunk, ...rest) {
    if (event === 'data' && chunk != null) onChunk(chunk);
    return emit.call(this, event, chunk, ...rest);
  };
}

function collector() {
  const chunks = [];
  let size = 0;
  return {
    add(chunk) {
      if (size > MAX_BYTES) return;
      const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(String(chunk));
      chunks.push(buf);
      size += buf.length;
    },
    text() {
      return Buffer.concat(chunks).toString('utf8');
    },
  };
}

/** Wraps res.write/res.end to collect an outgoing body, calling done(text) at the end. */
function watchWrites(res, done) {
  const body = collector();
  const write = res.write;
  const end = res.end;
  res.write = function (chunk, ...rest) {
    if (chunk != null && typeof chunk !== 'function') body.add(chunk);
    return write.call(this, chunk, ...rest);
  };
  res.end = function (chunk, ...rest) {
    if (chunk != null && typeof chunk !== 'function') body.add(chunk);
    done(body.text());
    return end.call(this, chunk, ...rest);
  };
}

/** instrumentation-http hooks: servers record the request received and response sent; clients the reverse. */
const httpHooks = {
  requestHook(span, request) {
    if (!captureRequested()) return;
    if (request instanceof http.IncomingMessage) {
      const body = collector();
      watchData(request, (chunk) => body.add(chunk));
      span.__sdnaRequestBody = body;
    } else if (request instanceof http.ClientRequest) {
      watchWrites(request, (text) => {
        if (text) span.setAttribute('sdna.request.body', redact(text));
      });
    }
  },
  responseHook(span, response) {
    if (response instanceof http.ServerResponse) {
      if (!span.__sdnaRequestBody) return;
      watchWrites(response, (text) => {
        const requestText = span.__sdnaRequestBody.text();
        if (requestText) span.setAttribute('sdna.request.body', redact(requestText));
        if (text) span.setAttribute('sdna.response.body', redact(text));
        span.setAttribute('sdna.captured', true);
      });
    } else if (response instanceof http.IncomingMessage && captureRequested()) {
      const body = collector();
      watchData(response, (chunk) => body.add(chunk));
      response.once('end', () => {
        const text = body.text();
        if (text) span.setAttribute('sdna.response.body', redact(text));
      });
    }
  },
};

/**
 * Records a value computed inside the service on the current span, for test runs:
 *   capture('order.total', total)
 * Does nothing unless the request is a ServiceDNA capture run.
 */
function capture(name, value) {
  if (!captureRequested()) return;
  const span = trace.getActiveSpan();
  if (!span) return;
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  span.setAttribute(`sdna.capture.${name}`, redact(text ?? String(value)));
}

module.exports = { httpHooks, capture, redact, captureRequested };
