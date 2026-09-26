package com.servicedna.traces;

import com.servicedna.common.exception.ApiException;
import com.servicedna.common.filter.AttributeFilter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Builds TraceQL from explorer filters, so users search with fields rather than query syntax. */
public final class TraceQl {

  private TraceQl() {}

  /** Explorer filters; every one is optional. {@code attributes} are "key op value" (see {@link #attribute}). */
  public record Filters(
      String service,
      String operation,
      String environment,
      /** "error", "ok", or null for both. */
      String status,
      Long minDurationMs,
      Long maxDurationMs,
      List<String> attributes,
      /** Text inside captured request/response bodies, e.g. an order id. */
      String text) {}


  /** The span selector for the filters: all conditions must hold on one span. */
  static String query(Filters f) {
    List<String> conditions = new ArrayList<>();
    if (present(f.service())) {
      conditions.add("resource.service.name = " + quote(f.service()));
    }
    if (present(f.operation())) {
      conditions.add("name = " + quote(f.operation()));
    }
    if (present(f.environment())) {
      // SDKs send deployment.environment.name; older OpenTelemetry setups deployment.environment.
      conditions.add("(resource.deployment.environment.name = " + quote(f.environment())
          + " || resource.deployment.environment = " + quote(f.environment()) + ")");
    }
    if ("error".equalsIgnoreCase(f.status())) {
      conditions.add("status = error");
    } else if ("ok".equalsIgnoreCase(f.status())) {
      conditions.add("status != error");
    }
    if (f.minDurationMs() != null) {
      conditions.add("duration >= " + f.minDurationMs() + "ms");
    }
    if (f.maxDurationMs() != null) {
      conditions.add("duration <= " + f.maxDurationMs() + "ms");
    }
    if (f.attributes() != null) {
      f.attributes().stream().filter(TraceQl::present).map(TraceQl::attribute).forEach(conditions::add);
    }
    if (present(f.text())) {
      // Literal text: regex metacharacters escaped (TraceQL regexes are Go RE2).
      String pattern = quote(".*" + f.text().trim().replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0") + ".*");
      conditions.add("(span.sdna.request.body =~ " + pattern + " || span.sdna.response.body =~ " + pattern + ")");
    }
    return conditions.isEmpty() ? "{ }" : "{ " + String.join(" && ", conditions) + " }";
  }

  /**
   * One attribute condition: {@code http.response.status_code >= 500}, {@code orderId = o-17},
   * {@code resource.service.version = 1.2.0}. Unscoped keys match span or resource attributes;
   * numbers and booleans compare as such, anything else as a string (quotes optional).
   */
  static String attribute(String filter) {
    AttributeFilter f = AttributeFilter.parse(filter);
    String key = f.key();
    String scoped = key.startsWith("span.") || key.startsWith("resource.") ? key : "." + key;
    boolean literal = !f.quoted() && !f.operator().contains("~")
        && (f.valueIsNumber() || f.value().equals("true") || f.value().equals("false"));
    return scoped + " " + f.operator() + " " + (literal ? f.value() : quote(f.value()));
  }

  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  public static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
