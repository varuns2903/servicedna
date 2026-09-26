package com.servicedna.graph.domain;

import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.List;

/** How a call was made, from OpenTelemetry semantic-convention attributes. */
public enum Protocol {
  HTTP,
  GRPC,
  GRAPHQL,
  MESSAGING,
  DATABASE,
  OTHER;

  public static Protocol of(List<KeyValue> attributes) {
    if (has(attributes, "graphql.operation.type") || has(attributes, "graphql.operation.name")) {
      return GRAPHQL;
    }
    String rpc = SpanAttributes.string(attributes, "rpc.system");
    if ("grpc".equals(rpc)) {
      return GRPC;
    }
    if (has(attributes, "messaging.system")) {
      return MESSAGING;
    }
    if (has(attributes, "db.system") || has(attributes, "db.system.name")) {
      return DATABASE;
    }
    if (has(attributes, "http.request.method") || has(attributes, "http.method")) {
      return HTTP;
    }
    return OTHER;
  }

  private static boolean has(List<KeyValue> attributes, String key) {
    return SpanAttributes.string(attributes, key) != null;
  }
}
