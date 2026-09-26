'use strict';

const { context } = require('@opentelemetry/api');
const { suppressTracing } = require('@opentelemetry/core');

/**
 * Checks the service's own health endpoint on an interval and reports the result to ServiceDNA,
 * which registers the service on first contact. Its HTTP calls are excluded from tracing so
 * heartbeats don't show up as the service's traffic.
 */
function startHeartbeat(config, log = console) {
  if (!config.localHealthUrl || !(config.heartbeatMs > 0)) {
    return () => {};
  }

  const tick = () =>
    context.with(suppressTracing(context.active()), async () => {
      const start = Date.now();
      let status = 'DOWN';
      let message;
      try {
        const res = await fetch(config.localHealthUrl, { signal: AbortSignal.timeout(5000) });
        const body = await res.json().catch(() => ({}));
        message = body.message ?? body.status ?? `HTTP ${res.status}`;
        if (res.ok) status = Date.now() - start > config.degradedThresholdMs ? 'DEGRADED' : 'HEALTHY';
      } catch (err) {
        message = err.message;
      }

      try {
        const res = await fetch(`${config.url}/api/v1/ping`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-API-Key': config.key },
          body: JSON.stringify({
            status,
            latencyMs: Date.now() - start,
            message: String(message).slice(0, 500),
            service: config.serviceName,
            environment: config.environment,
          }),
          signal: AbortSignal.timeout(5000),
        });
        if (!res.ok) log.warn(`[servicedna] heartbeat rejected: HTTP ${res.status}`);
      } catch (err) {
        log.warn(`[servicedna] heartbeat failed: ${err.message}`);
      }
    });

  const timer = setInterval(tick, config.heartbeatMs);
  timer.unref();
  return () => clearInterval(timer);
}

module.exports = { startHeartbeat };
