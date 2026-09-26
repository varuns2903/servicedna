package com.servicedna.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedna.common.exception.ApiException;
import com.servicedna.common.filter.AttributeFilter;
import org.junit.jupiter.api.Test;

class AttributeFilterTest {

  @Test
  void splitsKeyOperatorAndValue() {
    assertThat(AttributeFilter.parse("  http.response.status_code >=  500 ")).isEqualTo(new AttributeFilter("http.response.status_code", ">=", "500", false));
    assertThat(AttributeFilter.parse("orderId=o-17")).isEqualTo(new AttributeFilter("orderId", "=", "o-17", false));
    assertThat(AttributeFilter.parse("name=~\"GET .*\"")).isEqualTo(new AttributeFilter("name", "=~", "GET .*", true));
    assertThat(AttributeFilter.parse("a!=b").operator()).isEqualTo("!=");
    assertThat(AttributeFilter.parse("a<-1.5").valueIsNumber()).isTrue();
    assertThat(AttributeFilter.parse("a=1.").valueIsNumber()).isFalse();
    assertThat(AttributeFilter.parse("a=1.2.3").valueIsNumber()).isFalse();
  }

  @Test
  void rejectsMalformedFilters() {
    for (String bad : new String[] {"", "orderId o-17", "=x", "1a=b", "a=", "a=   ", "ключ=x", "a".repeat(1001) + "=b"}) {
      assertThatThrownBy(() -> AttributeFilter.parse(bad)).as(bad).isInstanceOf(ApiException.class);
    }
  }
}
