package com.servicedna.testrun;

import com.fasterxml.jackson.databind.JsonNode;

/** Test access to package-private assertion helpers. */
public final class AssertionEvaluatorAccess {
  private AssertionEvaluatorAccess() {}

  public static JsonNode at(JsonNode node, String path) {
    return AssertionEvaluator.at(node, path);
  }
}
