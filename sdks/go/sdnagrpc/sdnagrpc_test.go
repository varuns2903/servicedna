package sdnagrpc

import (
	"context"
	"net"
	"testing"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
)

// call runs one Health/Check against a traced server and returns the server span's attributes.
func call(t *testing.T, service, baggage string) map[string]string {
	t.Helper()
	recorder := tracetest.NewSpanRecorder()
	otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(recorder)))
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := grpc.NewServer(ServerOptions()...)
	healthpb.RegisterHealthServer(server, health.NewServer())
	go server.Serve(lis)
	defer server.Stop()

	conn, err := grpc.NewClient(lis.Addr().String(), grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	ctx := metadata.AppendToOutgoingContext(context.Background(), "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
	if baggage != "" {
		ctx = metadata.AppendToOutgoingContext(ctx, "baggage", baggage)
	}
	healthpb.NewHealthClient(conn).Check(ctx, &healthpb.HealthCheckRequest{Service: service})
	server.GracefulStop()

	for _, s := range recorder.Ended() {
		if s.SpanKind() == trace.SpanKindServer {
			out := map[string]string{}
			for _, a := range s.Attributes() {
				out[string(a.Key)] = a.Value.Emit()
			}
			return out
		}
	}
	t.Fatal("no server span")
	return nil
}

func TestACaptureRunRecordsTheMessages(t *testing.T) {
	a := call(t, "", "sdna.capture=1")
	if a["sdna.request.body"] != `{}` || a["sdna.response.body"] != `{"status":"SERVING"}` || a["sdna.captured"] != "true" {
		t.Fatalf("attributes: %v", a)
	}
}

func TestOrdinaryCallsAreLeftAlone(t *testing.T) {
	if a := call(t, "", ""); a["sdna.request.body"] != "" {
		t.Fatalf("recorded outside a capture run: %v", a)
	}
}

func TestCaptureOnErrorRecordsFailedCallsOnly(t *testing.T) {
	t.Setenv("SERVICEDNA_CAPTURE_ON_ERROR", "true")
	if a := call(t, "", ""); a["sdna.request.body"] != "" {
		t.Fatalf("a successful call was recorded: %v", a)
	}
	a := call(t, "payments", "") // unknown to the health server: NOT_FOUND
	if a["sdna.request.body"] != `{"service":"payments"}` || a["sdna.response.body"] != "" || a["sdna.captured_on_error"] != "true" {
		t.Fatalf("attributes: %v", a)
	}
}
