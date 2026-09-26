'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { resolveConfig } = require('../src/config');

function tempProject(pkg) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sdna-'));
  if (pkg) fs.writeFileSync(path.join(dir, 'package.json'), JSON.stringify(pkg));
  return dir;
}

test('disabled without url and key', () => {
  assert.equal(resolveConfig({}, {}, tempProject()).enabled, false);
  assert.equal(resolveConfig({}, { SERVICEDNA_URL: 'http://x' }, tempProject()).enabled, false);
});

test('two variables are enough; name and version come from package.json', () => {
  const config = resolveConfig(
    {},
    { SERVICEDNA_URL: 'http://sdna:8080/', SERVICEDNA_KEY: 'sdna_ik_x' },
    tempProject({ name: '@shop/checkout', version: '2.3.0' }),
  );
  assert.equal(config.enabled, true);
  assert.equal(config.url, 'http://sdna:8080');
  assert.equal(config.serviceName, 'checkout');
  assert.equal(config.version, '2.3.0');
  assert.equal(config.healthUrl, undefined);
});

test('OTEL_SERVICE_NAME wins over package.json', () => {
  const config = resolveConfig({}, { OTEL_SERVICE_NAME: 'orders' }, tempProject({ name: 'something-else' }));
  assert.equal(config.serviceName, 'orders');
});

test('heartbeat checks /health on PORT over loopback', () => {
  const config = resolveConfig({}, { PORT: '4004' }, tempProject());
  assert.equal(config.localHealthUrl, 'http://127.0.0.1:4004/health');
  const custom = resolveConfig({}, { PORT: '8080', SERVICEDNA_HEALTH_PATH: '/actuator/health' }, tempProject());
  assert.equal(custom.localHealthUrl, 'http://127.0.0.1:8080/actuator/health');
});

test('environment comes from SERVICEDNA_ENV, then NODE_ENV', () => {
  assert.equal(resolveConfig({}, { SERVICEDNA_ENV: 'prod', NODE_ENV: 'production' }, tempProject()).environment, 'prod');
  assert.equal(resolveConfig({}, { NODE_ENV: 'production' }, tempProject()).environment, 'production');
});
