'use strict';

const { NodeSDK } = require('@opentelemetry/sdk-node');
const { getNodeAutoInstrumentations } = require('@opentelemetry/auto-instrumentations-node');
const { OTLPTraceExporter } = require('@opentelemetry/exporter-trace-otlp-proto');
const { resourceFromAttributes } = require('@opentelemetry/resources');
const { resolveConfig } = require('./config');
const { startHeartbeat } = require('./heartbeat');
const { httpHooks, capture } = require('./capture');

let running;

/**
 * Starts tracing and heartbeats. Call before the rest of the application loads (or use
 * `node -r @servicedna/node/register app.js`) so libraries are instrumented as they're required.
 * Without SERVICEDNA_URL and SERVICEDNA_KEY this logs a warning and does nothing.
 *
 * @returns {{ shutdown: () => Promise<void> }}
 */
function start(options = {}) {
  if (running) return running;
  const config = resolveConfig(options);
  const log = options.logger ?? console;
  if (!config.enabled) {
    log.warn('[servicedna] SERVICEDNA_URL and SERVICEDNA_KEY are not set; not sending telemetry');
    running = { shutdown: async () => {} };
    return running;
  }

  // telemetry.sdk.language is set explicitly: ServiceDNA reads it at registration, and it isn't
  // guaranteed to be on the resource exported with each span batch otherwise.
  const attributes = { 'service.name': config.serviceName, 'telemetry.sdk.language': 'nodejs' };
  if (config.environment) attributes['deployment.environment.name'] = config.environment;
  if (config.version) attributes['service.version'] = config.version;
  if (config.healthUrl) attributes['servicedna.health.url'] = config.healthUrl;

  const healthPath = config.localHealthUrl ? new URL(config.localHealthUrl).pathname : undefined;
  const sdk = new NodeSDK({
    resource: resourceFromAttributes(attributes),
    traceExporter: new OTLPTraceExporter({
      url: `${config.url}/api/v1/otlp/v1/traces`,
      headers: { 'x-servicedna-key': config.key },
    }),
    instrumentations: [
      getNodeAutoInstrumentations({
        // File-system spans are noise for service-to-service tracing.
        '@opentelemetry/instrumentation-fs': { enabled: false },
        // Health probes (ServiceDNA's and the heartbeat's) aren't traffic worth tracing.
        '@opentelemetry/instrumentation-http': {
          ignoreIncomingRequestHook: (req) => Boolean(healthPath) && req.url?.split('?')[0] === healthPath,
          // Bodies are recorded only for ServiceDNA test runs (baggage sdna.capture=1).
          requestHook: httpHooks.requestHook,
          responseHook: httpHooks.responseHook,
        },
      }),
      ...(options.instrumentations ?? []),
    ],
  });
  sdk.start();
  const stopHeartbeat = startHeartbeat(config, log);

  running = {
    config,
    shutdown: async () => {
      stopHeartbeat();
      await sdk.shutdown();
    },
  };
  for (const signal of ['SIGTERM', 'SIGINT']) {
    process.once(signal, () => {
      running.shutdown().finally(() => process.kill(process.pid, signal));
    });
  }
  log.info?.(
    `[servicedna] sending ${config.serviceName}${config.environment ? ` (${config.environment})` : ''} telemetry to ${config.url}`,
  );
  return running;
}

module.exports = { start, resolveConfig, capture };
