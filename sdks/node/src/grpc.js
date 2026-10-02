'use strict';

const { trace } = require('@opentelemetry/api');
const { InstrumentationBase, InstrumentationNodeModuleDefinition } = require('@opentelemetry/instrumentation');
const { captureOnError, captureRequested, redact } = require('./capture');

/**
 * gRPC message capture for @grpc/grpc-js servers: a unary call's request and response messages
 * are recorded on its server span as JSON (masked and truncated like HTTP bodies) — for ServiceDNA
 * test runs (baggage sdna.capture=1), and for failed calls with SERVICEDNA_CAPTURE_ON_ERROR=true.
 *
 * Registered after OpenTelemetry's gRPC instrumentation, it wraps each handler before OpenTelemetry
 * does, so at call time it runs inside the server span with the caller's baggage.
 */
class GrpcCaptureInstrumentation extends InstrumentationBase {
  constructor(config = {}) {
    super('@servicedna/node/grpc-capture', '0.1.0', config);
  }

  init() {
    return [
      new InstrumentationNodeModuleDefinition(
        '@grpc/grpc-js',
        ['>=1.0.0 <2'],
        (grpc) => {
          const proto = grpc.Server.prototype;
          if (proto.register.__servicednaCapture) return grpc;
          // Wrapped by hand: the instrumentation helpers unwrap an already-wrapped method first,
          // which would remove OpenTelemetry's own gRPC wrapper. This one chains onto it.
          const original = proto.register;
          const register = function register(name, handler, serialize, deserialize, type) {
            return original.call(this, name, type === 'unary' ? captureUnary(handler) : handler, serialize, deserialize, type);
          };
          register.__servicednaCapture = true;
          register.__servicednaOriginal = original;
          proto.register = register;
          return grpc;
        },
        (grpc) => {
          const proto = grpc && grpc.Server.prototype;
          if (proto && proto.register.__servicednaCapture) proto.register = proto.register.__servicednaOriginal;
        },
      ),
    ];
  }
}

function captureUnary(handler) {
  return function captured(call, callback) {
    const requested = captureRequested();
    if (!requested && !captureOnError()) return handler.call(this, call, callback);
    const span = trace.getActiveSpan();
    const done = (err, value, ...rest) => {
      if (requested || err) record(span, call.request, err ? undefined : value, requested);
      return callback(err, value, ...rest);
    };
    try {
      return handler.call(this, call, done);
    } catch (err) {
      record(span, call.request, undefined, requested);
      throw err;
    }
  };
}

function record(span, request, response, requested) {
  if (!span || !span.isRecording()) return;
  span.setAttribute('sdna.request.body', redact(json(request)));
  if (response !== undefined) span.setAttribute('sdna.response.body', redact(json(response)));
  span.setAttribute(requested ? 'sdna.captured' : 'sdna.captured_on_error', true);
}

function json(message) {
  try {
    return JSON.stringify(message) ?? String(message);
  } catch {
    return String(message);
  }
}

module.exports = { GrpcCaptureInstrumentation };
