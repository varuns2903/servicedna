package com.servicedna.graph.domain;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.List;

/** Reads OTLP attributes, accepting the current and previous semantic-convention names. */
public final class SpanAttributes {

  private SpanAttributes() {}

  /** The first of {@code keys} present, as a string; null if none is. */
  public static String string(List<KeyValue> attributes, String... keys) {
    for (String key : keys) {
      for (KeyValue kv : attributes) {
        if (kv.getKey().equals(key)) {
          AnyValue v = kv.getValue();
          String s =
              switch (v.getValueCase()) {
                case STRING_VALUE -> v.getStringValue();
                case INT_VALUE -> Long.toString(v.getIntValue());
                case BOOL_VALUE -> Boolean.toString(v.getBoolValue());
                case DOUBLE_VALUE -> Double.toString(v.getDoubleValue());
                default -> null;
              };
          if (s != null && !s.isBlank()) {
            return s;
          }
        }
      }
    }
    return null;
  }
}
