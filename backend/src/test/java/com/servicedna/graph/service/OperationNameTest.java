package com.servicedna.graph.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;

class OperationNameTest {

  private static KeyValue attr(String key, String value) {
    return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
  }

  private static Span server(KeyValue... attributes) {
    return Span.newBuilder().setKind(Span.SpanKind.SPAN_KIND_SERVER).setName("POST /graphql")
        .addAllAttributes(java.util.List.of(attributes)).build();
  }

  @Test
  void aGraphqlRequestIsNamedAfterItsOperation() {
    KeyValue method = attr("http.request.method", "POST");
    KeyValue route = attr("http.route", "/graphql");
    assertThat(ObservedCallCollector.operationName(server(method, route, attr("graphql.operation.type", "query"),
        attr("graphql.operation.name", "ListProducts")))).isEqualTo("query ListProducts");
    assertThat(ObservedCallCollector.operationName(server(method, route, attr("graphql.operation.type", "mutation"))))
        .isEqualTo("mutation");
    assertThat(ObservedCallCollector.operationName(server(method, route))).isEqualTo("POST /graphql");
  }
}
