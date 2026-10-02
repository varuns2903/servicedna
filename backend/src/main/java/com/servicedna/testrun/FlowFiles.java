package com.servicedna.testrun;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads test flow files (flows/*.yaml) the way `sdna test run` does, for runs started by
 * integrations such as the GitHub App:
 *
 * <pre>
 * name: Checkout
 * environment: staging
 * cases:
 *   - name: an order is paid
 *     request: {service: api-gateway, method: POST, path: /api/orders, body: {userId: u-1}}
 *     expect:
 *       - {entry: true, status: 201}
 *       - {service: payment-service, operation: Charge, exists: true}
 * </pre>
 */
public final class FlowFiles {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
  private static final ObjectMapper JSON = new ObjectMapper();

  private FlowFiles() {}

  /** A flow file that couldn't be read, with why. */
  public static class InvalidFlowException extends RuntimeException {
    public InvalidFlowException(String message) {
      super(message);
    }
  }

  /** The suite a flow file describes; {@code fileName} names it when the file doesn't. */
  public static TestRunDto.StartSuite parse(String yaml, String fileName) {
    JsonNode root;
    try {
      root = YAML.readTree(yaml);
    } catch (Exception e) {
      throw new InvalidFlowException(fileName + ": not valid YAML (" + firstLine(e.getMessage()) + ")");
    }
    if (root == null || !root.path("cases").isArray() || root.path("cases").isEmpty()) {
      throw new InvalidFlowException(fileName + ": no cases");
    }
    String name = root.hasNonNull("name") ? root.get("name").asText() : fileName.replaceFirst("\\.[^.]*$", "");
    String environment = root.hasNonNull("environment") ? root.get("environment").asText() : null;
    List<TestRunDto.Case> cases = new ArrayList<>();
    int i = 0;
    for (JsonNode c : root.get("cases")) {
      i++;
      String caseName = c.hasNonNull("name") ? c.get("name").asText() : "case " + i;
      try {
        cases.add(new TestRunDto.Case(caseName, request(c.path("request")), assertions(c.path("expect"))));
      } catch (RuntimeException e) {
        throw new InvalidFlowException(fileName + ": case " + i + " (" + caseName + "): " + e.getMessage());
      }
    }
    return new TestRunDto.StartSuite(name, null, environment, cases);
  }

  static TestRunDto.CreateRequest request(JsonNode r) {
    if (!r.isObject()) {
      throw new IllegalArgumentException("request is missing");
    }
    JsonNode body = r.get("body");
    String bodyText = body == null || body.isNull() ? null : body.isTextual() ? body.asText() : body.toString();
    Map<String, String> headers = null;
    if (r.path("headers").isObject()) {
      headers = new java.util.LinkedHashMap<>();
      for (Iterator<Map.Entry<String, JsonNode>> it = r.get("headers").fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> h = it.next();
        headers.put(h.getKey(), h.getValue().asText());
      }
    }
    String protocol = r.hasNonNull("protocol") ? r.get("protocol").asText().toUpperCase(Locale.ROOT) : "HTTP";
    return new TestRunDto.CreateRequest(
        text(r, "environment"),
        com.servicedna.graph.domain.Protocol.valueOf(protocol),
        null,
        text(r, "service") != null ? text(r, "service") : text(r, "serviceName"),
        text(r, "method"),
        text(r, "path"),
        text(r, "grpcMethod"),
        text(r, "topic"),
        text(r, "key"),
        headers,
        bodyText,
        r.hasNonNull("testMode") ? r.get("testMode").asBoolean() : null);
  }

  /** {entry: true, …} and {service: x, operation: y, …} are shorthands for an explicit target. */
  static JsonNode assertions(JsonNode expect) {
    ArrayNode out = JsonNodeFactory.instance.arrayNode();
    if (!expect.isArray()) {
      return out;
    }
    for (JsonNode e : expect) {
      ObjectNode assertion = JSON.createObjectNode();
      ObjectNode target = JSON.createObjectNode();
      for (Iterator<Map.Entry<String, JsonNode>> it = e.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> f = it.next();
        switch (f.getKey()) {
          case "entry", "service", "operation" -> target.set(f.getKey(), f.getValue());
          default -> assertion.set(f.getKey(), f.getValue());
        }
      }
      if (!assertion.has("target")) {
        assertion.set("target", target);
      }
      out.add(assertion);
    }
    return out;
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static String firstLine(String message) {
    return message == null ? "unreadable" : message.lines().findFirst().orElse("unreadable");
  }
}
