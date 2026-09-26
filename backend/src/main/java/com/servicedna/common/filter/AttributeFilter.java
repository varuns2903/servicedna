package com.servicedna.common.filter;

import com.servicedna.common.exception.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * One attribute filter as users write it — {@code orderId=o-17}, {@code status >= 500},
 * {@code name=~"GET .*"} — split into key, operator and value. Parsed by hand, not with a regular
 * expression, so user input can't make it backtrack.
 */
public record AttributeFilter(String key, String operator, String value, boolean quoted) {

  public static final int MAX_LENGTH = 1000;
  // Longest first, so ">=" isn't read as ">".
  private static final List<String> OPERATORS = List.of("!=", ">=", "<=", "=~", "!~", "=", ">", "<");

  public static AttributeFilter parse(String filter) {
    String text = filter == null ? "" : filter.strip();
    if (text.isEmpty() || text.length() > MAX_LENGTH) {
      throw invalid(filter);
    }
    int i = 0;
    char first = text.charAt(0);
    if (!(Character.isLetter(first) && first < 128 || first == '_')) {
      throw invalid(filter);
    }
    while (i < text.length() && isKeyChar(text.charAt(i))) {
      i++;
    }
    String key = text.substring(0, i);
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    String rest = text.substring(i);
    String operator = OPERATORS.stream().filter(rest::startsWith).findFirst().orElseThrow(() -> invalid(filter));
    String raw = rest.substring(operator.length()).strip();
    if (raw.isEmpty()) {
      throw invalid(filter);
    }
    boolean quoted = raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"");
    return new AttributeFilter(key, operator, quoted ? raw.substring(1, raw.length() - 1) : raw, quoted);
  }

  /** A plain decimal number, e.g. 500 or -1.5. */
  public boolean valueIsNumber() {
    String v = value.startsWith("-") ? value.substring(1) : value;
    int dot = v.indexOf('.');
    String whole = dot < 0 ? v : v.substring(0, dot);
    String fraction = dot < 0 ? "0" : v.substring(dot + 1);
    return !whole.isEmpty() && !fraction.isEmpty() && digits(whole) && digits(fraction);
  }

  private static boolean digits(String s) {
    return s.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  private static boolean isKeyChar(char c) {
    return c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-');
  }

  private static ApiException invalid(String filter) {
    String shown = filter == null ? "" : filter.length() > 100 ? filter.substring(0, 100) + "…" : filter;
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Filters look like key=value, key>=500 or key=~regex: " + shown);
  }
}
