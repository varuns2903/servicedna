package io.github.varuns2903.servicedna;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/** ServiceDNA test-run helpers. */
public final class ServiceDna {

  static final int MAX_CAPTURE_BYTES =
      Integer.parseInt(System.getenv().getOrDefault("SERVICEDNA_CAPTURE_MAX_BYTES", "16384"));
  private static final Pattern SENSITIVE =
      Pattern.compile("pass(word|wd)?|secret|token|api[-_.]?key|authorization|cookie|session|card|cvv|ssn", Pattern.CASE_INSENSITIVE);
  private static final ObjectMapper JSON = new ObjectMapper();

  private ServiceDna() {}

  /** Whether the current request is a ServiceDNA capture run (baggage {@code sdna.capture=1}). */
  public static boolean captureRequested() {
    return "1".equals(Baggage.current().getEntryValue("sdna.capture"));
  }

  /**
   * Records a value computed inside the service on the current span, for test runs:
   * {@code ServiceDna.capture("order.total", total)}. Does nothing outside a capture run.
   */
  public static void capture(String name, Object value) {
    if (!captureRequested()) {
      return;
    }
    Span span = Span.current();
    if (!span.isRecording()) {
      return;
    }
    String text;
    try {
      text = value instanceof String s ? s : JSON.writeValueAsString(value);
    } catch (Exception e) {
      text = String.valueOf(value);
    }
    span.setAttribute("sdna.capture." + name, redact(text));
  }

  /**
   * Tags the current span with a business key, so ServiceDNA can follow it across services, traces
   * and logs — even where trace context was lost: {@code ServiceDna.tag("orderId", order.getId())}.
   * Unlike {@link #capture}, it applies to all traffic; use ids, not personal data.
   */
  public static void tag(String name, Object value) {
    Span span = Span.current();
    if (value != null && span.isRecording()) {
      span.setAttribute("sdna.key." + name, String.valueOf(value));
    }
  }

  /** Masks credential-like fields in JSON (other text is kept) and truncates. */
  public static String redact(String text) {
    String out = text;
    try {
      JsonNode node = JSON.readTree(text);
      if (node != null && (node.isObject() || node.isArray())) {
        out = JSON.writeValueAsString(mask(node));
      }
    } catch (Exception ignored) {
      // not JSON
    }
    return out.length() <= MAX_CAPTURE_BYTES ? out : out.substring(0, MAX_CAPTURE_BYTES) + "…[truncated]";
  }

  private static JsonNode mask(JsonNode node) {
    if (node instanceof ObjectNode object) {
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        field.setValue(SENSITIVE.matcher(field.getKey()).find() ? TextNode.valueOf("[masked]") : mask(field.getValue()));
      }
    } else if (node instanceof ArrayNode array) {
      for (int i = 0; i < array.size(); i++) {
        array.set(i, mask(array.get(i)));
      }
    }
    return node;
  }
}
