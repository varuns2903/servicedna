'use strict';

const util = require('node:util');
const { logs, SeverityNumber } = require('@opentelemetry/api-logs');

const LEVELS = {
  debug: [SeverityNumber.DEBUG, 'DEBUG'],
  log: [SeverityNumber.INFO, 'INFO'],
  info: [SeverityNumber.INFO, 'INFO'],
  warn: [SeverityNumber.WARN, 'WARN'],
  error: [SeverityNumber.ERROR, 'ERROR'],
};

/**
 * Sends console.log/info/warn/error/debug to ServiceDNA as log records, in the active trace's
 * context — so a request's logs appear on its trace. Output still goes to the console as before.
 * Returns a function that restores the original console methods.
 */
function bridgeConsole(target = console) {
  const logger = logs.getLogger('console');
  const originals = {};
  let emitting = false;
  for (const [method, [severityNumber, severityText]] of Object.entries(LEVELS)) {
    const original = target[method];
    originals[method] = original;
    target[method] = function (...args) {
      // A log call made while exporting (e.g. the exporter reporting an error) isn't sent again.
      if (!emitting) {
        emitting = true;
        try {
          logger.emit({ severityNumber, severityText, body: util.format(...args) });
        } catch {
          // never let logging break the application
        } finally {
          emitting = false;
        }
      }
      return original.apply(this, args);
    };
  }
  return () => Object.assign(target, originals);
}

module.exports = { bridgeConsole };
