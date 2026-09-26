'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * Resolves SDK settings from options and environment variables. Only SERVICEDNA_URL and
 * SERVICEDNA_KEY are required; everything else has a default.
 */
function resolveConfig(options = {}, env = process.env, cwd = process.cwd()) {
  const url = trimSlashes(options.url ?? env.SERVICEDNA_URL ?? '');
  const key = options.key ?? env.SERVICEDNA_KEY ?? '';
  const port = env.PORT;
  return {
    url,
    key,
    enabled: Boolean(url && key),
    serviceName:
      options.serviceName ?? env.OTEL_SERVICE_NAME ?? env.SERVICEDNA_SERVICE ?? packageName(cwd) ?? 'unknown_service:node',
    environment: options.environment ?? env.SERVICEDNA_ENV ?? env.NODE_ENV ?? undefined,
    version: options.version ?? env.SERVICEDNA_VERSION ?? packageVersion(cwd),
    // What ServiceDNA should probe: only reported when set, since a service can't know the
    // address ServiceDNA reaches it on.
    healthUrl: options.healthUrl ?? env.SERVICEDNA_HEALTH_URL ?? undefined,
    // What the heartbeat checks, over loopback.
    localHealthUrl:
      options.localHealthUrl ??
      env.SERVICEDNA_LOCAL_HEALTH_URL ??
      (port ? `http://127.0.0.1:${port}${env.SERVICEDNA_HEALTH_PATH ?? '/health'}` : undefined),
    heartbeatMs: Number(options.heartbeatMs ?? env.SERVICEDNA_HEARTBEAT_MS ?? 15000),
    // Logs go to ServiceDNA next to traces (pino and bunyan automatically, console.* unless
    // SERVICEDNA_LOGS_CONSOLE=false); SERVICEDNA_LOGS=false turns log export off.
    logs: flag(options.logs ?? env.SERVICEDNA_LOGS, true),
    consoleLogs: flag(options.consoleLogs ?? env.SERVICEDNA_LOGS_CONSOLE, true),
    degradedThresholdMs: Number(options.degradedThresholdMs ?? env.SERVICEDNA_DEGRADED_MS ?? 2000),
  };
}

function trimSlashes(url) {
  let end = url.length;
  while (end > 0 && url[end - 1] === '/') end -= 1;
  return url.slice(0, end);
}

function flag(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'boolean') return value;
  return !/^(0|false|no|off)$/i.test(String(value));
}

function readPackageJson(cwd) {
  try {
    return JSON.parse(fs.readFileSync(path.join(cwd, 'package.json'), 'utf8'));
  } catch {
    return undefined;
  }
}

function packageName(cwd) {
  const name = readPackageJson(cwd)?.name;
  return name ? name.replace(/^@[^/]+\//, '') : undefined;
}

function packageVersion(cwd) {
  return readPackageJson(cwd)?.version;
}

module.exports = { resolveConfig };
