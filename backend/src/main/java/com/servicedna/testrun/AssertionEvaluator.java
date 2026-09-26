package com.servicedna.testrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Evaluates a test case's assertions against its run. An assertion targets the entry call
 * ({@code "target": {"entry": true}}) or a hop ({@code "target": {"service": "payments",
 * "operation": "POST /charge"}}, operation optional) and checks any of:
 *
 * <pre>
 * "exists": true | false                 the hop happened (or didn't)
 * "status": 201                          HTTP status (gRPC code for gRPC)
 * "latencyMs": {"lt": 500}
 * "request":  {"items.0.qty": 2}         fields of the body it received (dot paths)
 * "response": {"status": "CAPTURED"}     fields of the body it returned
 * "captured": {"order.total": 59}        values recorded with capture()
 * </pre>
 *
 * A check's expected value is a literal (equality) or one of {@code eq ne lt lte gt gte contains
 * matches exists}.
 */
public final class AssertionEvaluator {

  private final ObjectMapper json;

  public AssertionEvaluator(ObjectMapper json) {
    this.json = json;
  }

  public record Result(String description, boolean passed, String message) {}

  public List<Result> evaluate(JsonNode assertions, JsonNode entryResult, List<TestRunDto.Hop> hops) {
    List<Result> results = new ArrayList<>();
    if (assertions == null || !assertions.isArray()) {
      return results;
    }
    for (JsonNode assertion : assertions) {
      JsonNode target = assertion.path("target");
      boolean entry = target.path("entry").asBoolean(false);
      String service = target.path("service").asText(null);
      String operation = target.path("operation").asText(null);
      String label = entry ? "entry" : service + (operation != null ? " " + operation : "");

      TestRunDto.Hop hop = entry ? null : findHop(hops, service, operation);
      if (!entry && assertion.has("exists")) {
        boolean expected = assertion.get("exists").asBoolean();
        results.add(new Result(label + (expected ? " was reached" : " was not reached"), (hop != null) == expected,
            hop != null ? "it was reached" : "no matching hop"));
      }
      if (!entry && hop == null) {
        if (hasChecks(assertion)) {
          results.add(new Result(label, false, "no matching hop in the run"));
        }
        continue;
      }

      Integer status = entry ? (entryResult != null && entryResult.hasNonNull("status") ? entryResult.get("status").asInt() : null) : hop.httpStatus();
      Double latency = entry ? (entryResult != null && entryResult.hasNonNull("durationMs") ? entryResult.get("durationMs").asDouble() : null) : hop.durationMs();
      String request = entry ? null : hop.requestBody();
      String response = entry ? (entryResult != null ? entryResult.path("body").asText(null) : null) : hop.responseBody();

      if (assertion.has("status")) {
        results.add(check(label + " status", status == null ? null : json.getNodeFactory().numberNode(status), assertion.get("status")));
      }
      if (assertion.has("latencyMs")) {
        results.add(check(label + " latency", latency == null ? null : json.getNodeFactory().numberNode(latency), assertion.get("latencyMs")));
      }
      checkFields(results, label + " request", parse(request), assertion.path("request"));
      checkFields(results, label + " response", parse(response), assertion.path("response"));
      if (!entry && assertion.has("captured")) {
        for (Iterator<Map.Entry<String, JsonNode>> it = assertion.get("captured").fields(); it.hasNext(); ) {
          Map.Entry<String, JsonNode> field = it.next();
          String value = hop.captured().get(field.getKey());
          results.add(check(label + " captured " + field.getKey(), value == null ? null : parse(value), field.getValue()));
        }
      }
    }
    return results;
  }

  private static boolean hasChecks(JsonNode a) {
    return a.has("status") || a.has("latencyMs") || a.has("request") || a.has("response") || a.has("captured");
  }

  /** First hop of the service whose operation matches exactly, or contains the given text. */
  static TestRunDto.Hop findHop(List<TestRunDto.Hop> hops, String service, String operation) {
    if (hops == null || service == null) {
      return null;
    }
    TestRunDto.Hop partial = null;
    for (TestRunDto.Hop hop : hops) {
      if (!service.equals(hop.service())) {
        continue;
      }
      if (operation == null || operation.equals(hop.operation())) {
        return hop;
      }
      if (partial == null && hop.operation() != null && hop.operation().contains(operation)) {
        partial = hop;
      }
    }
    return partial;
  }

  private void checkFields(List<Result> results, String label, JsonNode body, JsonNode expectations) {
    if (!expectations.isObject()) {
      return;
    }
    for (Iterator<Map.Entry<String, JsonNode>> it = expectations.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> field = it.next();
      results.add(check(label + " " + field.getKey(), body == null ? null : at(body, field.getKey()), field.getValue()));
    }
  }

  /** Dot path into JSON: "lines.0.qty". */
  static JsonNode at(JsonNode node, String path) {
    JsonNode current = node;
    for (String part : path.split("\\.")) {
      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }
      current = current.isArray() && part.chars().allMatch(Character::isDigit) ? current.get(Integer.parseInt(part)) : current.get(part);
    }
    return current == null || current.isMissingNode() ? null : current;
  }

  private Result check(String label, JsonNode actual, JsonNode expected) {
    if (!expected.isObject()) {
      return compare(label, actual, "eq", expected);
    }
    Iterator<Map.Entry<String, JsonNode>> it = expected.fields();
    if (!it.hasNext()) {
      return new Result(label, false, "empty expectation");
    }
    Map.Entry<String, JsonNode> e = it.next();
    return compare(label, actual, e.getKey(), e.getValue());
  }

  private Result compare(String label, JsonNode actual, String op, JsonNode expected) {
    String description = label + " " + op + " " + expected;
    String got = actual == null ? "missing" : actual.isTextual() ? actual.asText() : actual.toString();
    boolean passed =
        switch (op) {
          case "exists" -> (actual != null) == expected.asBoolean(true);
          case "eq" -> actual != null && equal(actual, expected);
          case "ne" -> actual == null || !equal(actual, expected);
          case "lt", "lte", "gt", "gte" -> {
            if (actual == null || !actual.isNumber() && !isNumeric(actual.asText())) {
              yield false;
            }
            int cmp = new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText()));
            yield switch (op) {
              case "lt" -> cmp < 0;
              case "lte" -> cmp <= 0;
              case "gt" -> cmp > 0;
              default -> cmp >= 0;
            };
          }
          case "contains" -> actual != null && (actual.isTextual() ? actual.asText() : actual.toString()).contains(expected.asText());
          case "matches" -> actual != null && Pattern.compile(expected.asText()).matcher(actual.asText()).find();
          default -> false;
        };
    return new Result(description, passed, passed ? null : "got " + got);
  }

  private static boolean equal(JsonNode actual, JsonNode expected) {
    if (actual.isNumber() && expected.isNumber() || isNumeric(actual.asText()) && expected.isNumber()) {
      return new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText())) == 0;
    }
    if (expected.isValueNode() && actual.isValueNode()) {
      return Objects.equals(actual.asText(), expected.asText());
    }
    return actual.equals(expected);
  }

  private static boolean isNumeric(String s) {
    try {
      new BigDecimal(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private JsonNode parse(String text) {
    if (text == null) {
      return null;
    }
    try {
      return json.readTree(text);
    } catch (Exception e) {
      return json.getNodeFactory().textNode(text);
    }
  }
}
