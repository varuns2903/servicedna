package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssertionEvaluatorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AssertionEvaluator evaluator = new AssertionEvaluator(json);

  private static TestRunDto.Hop hop(String service, String operation, Integer status, double ms, String in, String out, Map<String, String> captured) {
    return new TestRunDto.Hop("s", null, service, operation, "SERVER", OffsetDateTime.now(), ms, false, null, status, in, out, new HashMap<>(captured));
  }

  private final List<TestRunDto.Hop> hops = List.of(
      hop("orders", "POST /orders", 201, 40, "{\"items\":[{\"qty\":2}]}", "{\"total\":59,\"status\":\"CONFIRMED\"}", Map.of("order.total", "59")),
      hop("payments", "shoplite.payments.Payment/Charge", 0, 5, null, null, Map.of()));

  private List<AssertionEvaluator.Result> run(String assertions) throws Exception {
    JsonNode entry = json.readTree("{\"status\":201,\"durationMs\":71,\"body\":\"{\\\"id\\\":\\\"o-1\\\"}\"}");
    return evaluator.evaluate(json.readTree(assertions), entry, hops);
  }

  @Test
  void passingChecks() throws Exception {
    List<AssertionEvaluator.Result> results = run("""
        [
          {"target": {"entry": true}, "status": 201, "latencyMs": {"lt": 500}, "response": {"id": "o-1"}},
          {"target": {"service": "orders", "operation": "POST /orders"},
           "status": 201, "request": {"items.0.qty": 2}, "response": {"total": {"gte": 59}, "status": {"matches": "CONF"}},
           "captured": {"order.total": 59}},
          {"target": {"service": "payments", "operation": "Charge"}, "exists": true, "status": 0},
          {"target": {"service": "fraud"}, "exists": false}
        ]""");
    assertThat(results).allSatisfy(r -> assertThat(r.passed()).as(r.description() + " " + r.message()).isTrue());
    assertThat(results).hasSize(11);
  }

  @Test
  void failingChecksSayWhatTheyGot() throws Exception {
    List<AssertionEvaluator.Result> results = run("""
        [
          {"target": {"entry": true}, "status": 200},
          {"target": {"service": "orders"}, "response": {"total": {"lt": 10}, "missing": {"exists": true}}},
          {"target": {"service": "notifications"}, "status": 202}
        ]""");
    assertThat(results).extracting(AssertionEvaluator.Result::passed).containsOnly(false);
    assertThat(results.get(0).message()).isEqualTo("got 201");
    assertThat(results.get(1).message()).isEqualTo("got 59");
    assertThat(results.get(2).message()).isEqualTo("got missing");
    assertThat(results.get(3).message()).isEqualTo("no matching hop in the run");
  }
}
