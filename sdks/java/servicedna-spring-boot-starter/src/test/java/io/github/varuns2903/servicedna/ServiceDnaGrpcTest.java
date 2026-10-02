package io.github.varuns2903.servicedna;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceDnaGrpcTest {

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
      .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
      .setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))
      .build();

  /** One Health/Check through a traced server; returns the server span. */
  private SpanData check(String service, String baggage, boolean captureOnError) throws Exception {
    String name = "health-" + UUID.randomUUID();
    var interceptors = List.of(new ServiceDnaGrpcServerInterceptor(captureOnError),
        io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry.create(otel).createServerInterceptor());
    Server server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(ServerInterceptors.intercept(new HealthStatusManager().getHealthService(), interceptors))
        .build().start();
    ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    Metadata headers = new Metadata();
    headers.put(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER), "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    if (baggage != null) {
      headers.put(Metadata.Key.of("baggage", Metadata.ASCII_STRING_MARSHALLER), baggage);
    }
    try {
      HealthGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
          .check(HealthCheckRequest.newBuilder().setService(service).build());
    } catch (StatusRuntimeException expected) {
      // unknown services are NOT_FOUND
    } finally {
      channel.shutdownNow();
      server.shutdownNow();
    }
    return exporter.getFinishedSpanItems().stream().filter(s -> s.getKind() == SpanKind.SERVER).reduce((a, b) -> b).orElseThrow();
  }

  private static String attr(SpanData span, String key) {
    return span.getAttributes().get(AttributeKey.stringKey(key));
  }

  @Test
  void aCaptureRunRecordsTheMessages() throws Exception {
    SpanData span = check("", "sdna.capture=1", false);
    assertThat(attr(span, "sdna.request.body")).isEqualTo("{}");
    assertThat(attr(span, "sdna.response.body")).isEqualTo("{\"status\":\"SERVING\"}");
    assertThat(span.getAttributes().get(AttributeKey.booleanKey("sdna.captured"))).isTrue();
  }

  @Test
  void ordinaryCallsAreLeftAlone() throws Exception {
    assertThat(attr(check("", null, false), "sdna.request.body")).isNull();
  }

  @Test
  void captureOnErrorRecordsFailedCallsOnly() throws Exception {
    assertThat(attr(check("", null, true), "sdna.request.body")).isNull();
    SpanData failed = check("payments", null, true);
    assertThat(attr(failed, "sdna.request.body")).isEqualTo("{\"service\":\"payments\"}");
    assertThat(attr(failed, "sdna.response.body")).isNull();
    assertThat(failed.getAttributes().get(AttributeKey.booleanKey("sdna.captured_on_error"))).isTrue();
  }

  @Test
  void theHelperOrdersTracingOutsideCapture() {
    var interceptors = ServiceDnaGrpc.serverInterceptors(otel);
    assertThat(interceptors.get(0)).isInstanceOf(ServiceDnaGrpcServerInterceptor.class); // runs last, inside the span
    assertThat(interceptors).hasSize(2);
  }
}
