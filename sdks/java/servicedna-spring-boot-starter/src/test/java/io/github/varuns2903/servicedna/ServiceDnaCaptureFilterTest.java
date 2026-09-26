package io.github.varuns2903.servicedna;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServiceDnaCaptureFilterTest {

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();

  private final FilterChain app = (req, res) -> {
    String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    ServiceDna.capture("seen", body.length());
    res.setContentType("application/json");
    res.getWriter().write("{\"price\":136.0,\"apiKey\":\"k\"}");
  };

  private SpanData run(boolean capture) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/quotes");
    request.setContent("{\"symbol\":\"AAPL\",\"password\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Span span = provider.get("test").spanBuilder("POST /quotes").startSpan();
    Context context = Context.current().with(span);
    if (capture) {
      context = context.with(Baggage.builder().put("sdna.capture", "1").build());
    }
    try (Scope ignored = context.makeCurrent()) {
      new ServiceDnaCaptureFilter().doFilter(request, response, app);
    } finally {
      span.end();
    }
    assertThat(response.getContentAsString()).isEqualTo("{\"price\":136.0,\"apiKey\":\"k\"}");
    return exporter.getFinishedSpanItems().get(exporter.getFinishedSpanItems().size() - 1);
  }

  @Test
  void recordsBodiesOfACaptureRunMaskingCredentials() throws Exception {
    SpanData span = run(true);
    assertThat(span.getAttributes().get(AttributeKey.stringKey("sdna.request.body")))
        .isEqualTo("{\"symbol\":\"AAPL\",\"password\":\"[masked]\"}");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("sdna.response.body")))
        .isEqualTo("{\"price\":136.0,\"apiKey\":\"[masked]\"}");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("sdna.capture.seen"))).isEqualTo("32"); // body length the app read
  }

  @Test
  void leavesOrdinaryRequestsAlone() throws Exception {
    SpanData span = run(false);
    assertThat(span.getAttributes().get(AttributeKey.stringKey("sdna.request.body"))).isNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("sdna.capture.seen"))).isNull();
  }
}
