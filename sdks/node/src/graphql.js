'use strict';

const { getRPCMetadata } = require('@opentelemetry/core');

/**
 * Names a GraphQL request after its operation. OpenTelemetry's GraphQL instrumentation records the
 * operation on its own spans inside the request, but ServiceDNA reads a service's operations from
 * the HTTP server span — which would show every GraphQL call as "POST /graphql". This copies the
 * operation onto that span: graphql.operation.type, and graphql.operation.name — the operation's
 * name, or for an anonymous one its top-level fields ("products,orders").
 *
 * The execute span only gets the operation's attributes after it starts, so they're copied when it
 * ends (the server span is still open then); top-level fields are copied as their resolvers start.
 */
class GraphqlOperationProcessor {
  constructor() {
    this.servers = new Map(); // execute span id → its request's server span
    this.rootFields = new WeakMap(); // server span → top-level fields seen
  }

  onStart(span, parentContext) {
    const server = getRPCMetadata(parentContext)?.span;
    if (!server || server === span || typeof server.setAttribute !== 'function') return;
    if (span.name === 'graphql.execute') {
      this.servers.set(span.spanContext().spanId, server);
      return;
    }
    const path = span.attributes?.['graphql.field.path'];
    if (typeof path !== 'string' || path.includes('.')) return; // nested fields' paths have dots
    const fields = this.rootFields.get(server) ?? [];
    if (fields.includes(path)) return;
    fields.push(path);
    this.rootFields.set(server, fields);
    server.setAttribute('graphql.operation.name', fields.join(',')); // a named operation overrides it on end
  }

  onEnd(span) {
    const id = span.spanContext().spanId;
    const server = this.servers.get(id);
    if (!server) return;
    this.servers.delete(id);
    const type = span.attributes['graphql.operation.type'];
    const name = span.attributes['graphql.operation.name'];
    if (type) server.setAttribute('graphql.operation.type', type);
    if (name) server.setAttribute('graphql.operation.name', name);
  }

  forceFlush() {
    return Promise.resolve();
  }

  shutdown() {
    this.servers.clear();
    return Promise.resolve();
  }
}

module.exports = { GraphqlOperationProcessor };
