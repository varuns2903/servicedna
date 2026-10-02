package io.github.varuns2903.servicedna;

import io.grpc.ServerInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.util.List;

/**
 * gRPC servers for ServiceDNA: tracing (with the caller's trace context and baggage) and message
 * capture for test runs.
 *
 * <pre>
 * ServerBuilder.forPort(9090)
 *     .addService(ServerInterceptors.intercept(new PaymentsService(), ServiceDnaGrpc.serverInterceptors(openTelemetry)))
 * </pre>
 *
 * With Spring gRPC, register the two as {@code @GlobalServerInterceptor} beans in this order.
 */
public final class ServiceDnaGrpc {

  private ServiceDnaGrpc() {}

  /**
   * The tracing interceptor and the capture interceptor, ordered for
   * {@link io.grpc.ServerInterceptors#intercept(io.grpc.ServerServiceDefinition, List)} (whose last
   * interceptor runs first): tracing outermost, so capture runs inside the server span.
   * Capture on error follows {@code SERVICEDNA_CAPTURE_ON_ERROR}.
   */
  public static List<ServerInterceptor> serverInterceptors(OpenTelemetry openTelemetry) {
    boolean onError = Boolean.parseBoolean(System.getenv().getOrDefault("SERVICEDNA_CAPTURE_ON_ERROR", "false"));
    return List.of(new ServiceDnaGrpcServerInterceptor(onError), GrpcTelemetry.create(openTelemetry).createServerInterceptor());
  }
}
