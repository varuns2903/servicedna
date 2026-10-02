package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedna.graph.domain.Protocol;
import org.junit.jupiter.api.Test;

/** The backend reads flow files as `sdna test run` does (see cli/testrun.go). */
class FlowFilesTest {

  @Test
  void readsAFlowWithShorthands() {
    TestRunDto.StartSuite suite = FlowFiles.parse("""
        environment: staging
        cases:
          - name: an order is paid
            request:
              service: api-gateway
              method: POST
              path: /api/orders
              headers: {x-test: "1"}
              body: {userId: u-1, items: [{productId: p-2}]}
            expect:
              - {entry: true, status: 201, latencyMs: {lt: 2000}}
              - {service: payment-service, operation: Charge, exists: true}
          - request: {protocol: messaging, topic: order.created, key: o-1, body: '{"id":"o-1"}'}
        """, "checkout.yaml");

    assertThat(suite.name()).isEqualTo("checkout");
    assertThat(suite.environment()).isEqualTo("staging");
    TestRunDto.Case first = suite.cases().get(0);
    assertThat(first.request().serviceName()).isEqualTo("api-gateway");
    assertThat(first.request().protocol()).isEqualTo(Protocol.HTTP);
    assertThat(first.request().headers()).containsEntry("x-test", "1");
    assertThat(first.request().body()).isEqualTo("{\"userId\":\"u-1\",\"items\":[{\"productId\":\"p-2\"}]}");
    assertThat(first.assertions().get(0).get("target").get("entry").asBoolean()).isTrue();
    assertThat(first.assertions().get(0).get("status").asInt()).isEqualTo(201);
    assertThat(first.assertions().get(1).get("target").get("operation").asText()).isEqualTo("Charge");
    TestRunDto.Case second = suite.cases().get(1);
    assertThat(second.name()).isEqualTo("case 2");
    assertThat(second.request().protocol()).isEqualTo(Protocol.MESSAGING);
    assertThat(second.request().body()).isEqualTo("{\"id\":\"o-1\"}");
  }

  @Test
  void explainsWhatsWrong() {
    assertThatThrownBy(() -> FlowFiles.parse("name: x\n", "a.yaml")).hasMessage("a.yaml: no cases");
    assertThatThrownBy(() -> FlowFiles.parse("cases: [{name: c}]", "b.yaml")).hasMessageContaining("b.yaml: case 1 (c): request is missing");
    assertThatThrownBy(() -> FlowFiles.parse("cases: [{request: {protocol: smtp}}]", "c.yaml")).hasMessageContaining("c.yaml: case 1");
    assertThatThrownBy(() -> FlowFiles.parse("cases: [\n", "d.yaml")).hasMessageContaining("d.yaml: not valid YAML");
  }
}
