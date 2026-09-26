package com.servicedna.traces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import com.servicedna.logs.LogQlAccess;
import com.servicedna.testrun.AssertionEvaluatorAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FilterSafetyTest {

  @Test
  @Timeout(2)
  void pathologicalFiltersAreRejectedQuickly() {
    String spaces = "a=" + " ".repeat(50_000) + "x";
    assertThatThrownBy(() -> TraceQl.attribute(spaces)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LogQlAccess.attribute(spaces)).isInstanceOf(ApiException.class);
    String almost = "a" + " ".repeat(900) + "=";
    assertThatThrownBy(() -> TraceQl.attribute(almost)).isInstanceOf(ApiException.class);
    assertThat(TraceQl.attribute("  orderId =  o-17  ")).isEqualTo(".orderId = \"o-17\"");
    assertThat(LogQlAccess.attribute(" status >= 500 ")).isEqualTo("status >= 500");
  }

  @Test
  void anOversizedArrayIndexIsJustMissing() throws Exception {
    var node = new ObjectMapper().readTree("{\"lines\":[{\"qty\":2}]}");
    assertThat(AssertionEvaluatorAccess.at(node, "lines.0.qty").asInt()).isEqualTo(2);
    assertThat(AssertionEvaluatorAccess.at(node, "lines.99999999999.qty")).isNull();
  }
}
