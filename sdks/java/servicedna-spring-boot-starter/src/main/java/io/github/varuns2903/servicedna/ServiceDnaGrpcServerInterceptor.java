package io.github.varuns2903.servicedna;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;

/**
 * Records unary gRPC calls' request and response messages on the server span as JSON (masked and
 * size-capped): for ServiceDNA test runs (baggage {@code sdna.capture=1}), and for failed calls
 * when capture on error is on. Must run inside OpenTelemetry's gRPC server interceptor, so the span
 * and the caller's baggage are current — {@link ServiceDnaGrpc#serverInterceptors} orders them.
 */
public class ServiceDnaGrpcServerInterceptor implements ServerInterceptor {

  private static final JsonFormat.Printer JSON = JsonFormat.printer().omittingInsignificantWhitespace().preservingProtoFieldNames();

  private final boolean captureOnError;

  public ServiceDnaGrpcServerInterceptor(boolean captureOnError) {
    this.captureOnError = captureOnError;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    boolean requested = ServiceDna.captureRequested();
    if (call.getMethodDescriptor().getType() != MethodDescriptor.MethodType.UNARY || (!requested && !captureOnError)) {
      return next.startCall(call, headers);
    }
    Span span = Span.current();
    Object[] request = new Object[1];
    Object[] response = new Object[1];
    ServerCall<ReqT, RespT> recording = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
      @Override
      public void sendMessage(RespT message) {
        response[0] = message;
        super.sendMessage(message);
      }

      @Override
      public void close(Status status, Metadata trailers) {
        if (requested || !status.isOk()) {
          record(span, request[0], status.isOk() ? response[0] : null, requested);
        }
        super.close(status, trailers);
      }
    };
    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(recording, headers)) {
      @Override
      public void onMessage(ReqT message) {
        request[0] = message;
        super.onMessage(message);
      }
    };
  }

  private static void record(Span span, Object request, Object response, boolean requested) {
    if (!span.isRecording() || request == null) {
      return;
    }
    span.setAttribute("sdna.request.body", ServiceDna.redact(json(request)));
    if (response != null) {
      span.setAttribute("sdna.response.body", ServiceDna.redact(json(response)));
    }
    span.setAttribute(requested ? "sdna.captured" : "sdna.captured_on_error", true);
  }

  private static String json(Object message) {
    if (message instanceof MessageOrBuilder proto) {
      try {
        return JSON.print(proto);
      } catch (Exception e) {
        // fall through
      }
    }
    return String.valueOf(message);
  }
}
