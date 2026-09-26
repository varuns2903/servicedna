package com.servicedna.logs;

import com.servicedna.common.exception.ApiException;
import com.servicedna.common.filter.AttributeFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Builds LogQL from log search filters. Loki keeps OTLP attributes with dots as underscores. */
public final class LogQl {

  private LogQl() {}

  public record Filters(String service, String environment, String level, String text, String traceId, List<String> attributes) {}

  private static final Map<String, String> LEVELS = Map.of(
      "debug", "debug|trace",
      "info", "info",
      "warn", "warn|warning",
      "error", "error|fatal|critical");
  private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-fA-F]{16,32}$");

  static String query(Filters f) {
    List<String> selector = new ArrayList<>();
    if (present(f.service())) {
      selector.add("service_name=" + quote(f.service()));
    }
    if (present(f.environment())) {
      selector.add("deployment_environment_name=" + quote(f.environment()));
    }
    if (selector.isEmpty()) {
      selector.add("service_name=~\".+\"");
    }
    StringBuilder q = new StringBuilder("{").append(String.join(", ", selector)).append("}");
    if (present(f.traceId())) {
      if (!TRACE_ID.matcher(f.traceId().trim()).matches()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRACE_ID", "Trace ids are 16 or 32 hex characters.");
      }
      q.append(" | trace_id=").append(quote(f.traceId().trim().toLowerCase()));
    }
    if (present(f.level())) {
      String levels = LEVELS.get(f.level().toLowerCase());
      if (levels == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Level is one of debug, info, warn, error.");
      }
      q.append(" | detected_level=~").append(quote(levels));
    }
    if (present(f.text())) {
      // Case-insensitive, literal text.
      q.append(" |~ ").append(quote("(?i)" + f.text().trim().replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0")));
    }
    if (f.attributes() != null) {
      f.attributes().stream().filter(LogQl::present).forEach(a -> q.append(" | ").append(attribute(a)));
    }
    return q.toString();
  }

  /** {@code orderId=o-17}, {@code http.status_code>=500}: numeric comparisons stay numeric. */
  static String attribute(String filter) {
    AttributeFilter f = AttributeFilter.parse(filter);
    String key = f.key().replace('.', '_').replace('-', '_');
    String op = f.operator();
    if (op.startsWith(">") || op.startsWith("<")) {
      if (!f.valueIsNumber()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", op + " compares numbers: " + filter);
      }
      return key + " " + op + " " + f.value();
    }
    return key + op + quote(f.value());
  }


  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  public static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
