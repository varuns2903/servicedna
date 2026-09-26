package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedna.common.exception.ApiException;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.traces.TraceDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestRunReplayTest {

  private static final Instant T = Instant.parse("2026-09-26T10:00:00Z");

  private static TraceDto.Span span(String id, String parent, String service, String name, String kind, Map<String, String> attributes) {
    return new TraceDto.Span(id, parent, service, name, kind, T, 5, false, null, attributes, List.of());
  }

  private static TraceDto.Trace trace(TraceDto.Span... spans) {
    return new TraceDto.Trace("t", T, 20, List.of(spans));
  }

  @Test
  void theEntryRequestIsRebuiltFromItsSpan() {
    TraceDto.Trace trace = trace(
        span("3", "2", "order-service", "POST /orders", "SERVER", Map.of("http.request.method", "POST", "url.path", "/orders", "sdna.request.body", "{\"x\":1}")),
        span("1", null, "api-gateway", "POST /api/orders", "SERVER",
            Map.of("http.request.method", "POST", "url.path", "/api/orders", "url.query", "dry=1", "sdna.request.body", "{\"userId\":\"u-1\"}")),
        span("2", "1", "api-gateway", "POST", "CLIENT", Map.of()));

    TestRunDto.CreateRequest r = TestRunReplay.rebuild(trace, null, "staging", null);
    assertThat(r.protocol()).isEqualTo(Protocol.HTTP);
    assertThat(r.serviceName()).isEqualTo("api-gateway");
    assertThat(r.method()).isEqualTo("POST");
    assertThat(r.path()).isEqualTo("/api/orders?dry=1");
    assertThat(r.body()).isEqualTo("{\"userId\":\"u-1\"}");
    assertThat(r.environment()).isEqualTo("staging");

    TestRunDto.CreateRequest inner = TestRunReplay.rebuild(trace, "3", "staging", false);
    assertThat(inner.serviceName()).isEqualTo("order-service");
    assertThat(inner.path()).isEqualTo("/orders");
    assertThat(inner.testMode()).isFalse();
  }

  @Test
  void grpcAndKafkaRequestsAreRebuiltToo() {
    TestRunDto.CreateRequest grpc = TestRunReplay.rebuild(trace(span("1", null, "payment-service", "/shop.Payment/Charge", "SERVER",
        Map.of("rpc.system", "grpc", "rpc.service", "shop.Payment", "rpc.method", "Charge", "sdna.request.body", "{\"amount\":5}"))), null, null, null);
    assertThat(grpc.protocol()).isEqualTo(Protocol.GRPC);
    assertThat(grpc.grpcMethod()).isEqualTo("shop.Payment/Charge");
    assertThat(grpc.body()).isEqualTo("{\"amount\":5}");

    TestRunDto.CreateRequest kafka = TestRunReplay.rebuild(trace(span("1", null, "notification-service", "process order.created", "CONSUMER",
        Map.of("messaging.system", "kafka", "messaging.destination.name", "order.created", "messaging.kafka.message.key", "o-17",
            "sdna.request.body", "{\"id\":\"o-17\"}"))), null, null, null);
    assertThat(kafka.protocol()).isEqualTo(Protocol.MESSAGING);
    assertThat(kafka.topic()).isEqualTo("order.created");
    assertThat(kafka.key()).isEqualTo("o-17");
  }

  @Test
  void requestsWithoutTheirBodyCantBeReplayed() {
    TraceDto.Trace noBody = trace(span("1", null, "api-gateway", "POST /api/orders", "SERVER", Map.of("http.request.method", "POST", "url.path", "/api/orders")));
    assertThatThrownBy(() -> TestRunReplay.rebuild(noBody, null, null, null))
        .isInstanceOf(ApiException.class).hasMessageContaining("SERVICEDNA_CAPTURE_ON_ERROR");

    TraceDto.Trace get = trace(span("1", null, "api-gateway", "GET /api/orders/{id}", "SERVER", Map.of("http.method", "GET", "http.target", "/api/orders/o-1")));
    assertThat(TestRunReplay.rebuild(get, null, null, null).path()).isEqualTo("/api/orders/o-1");
  }
}
