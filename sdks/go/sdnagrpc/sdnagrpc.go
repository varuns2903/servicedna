// Package sdnagrpc traces gRPC servers and clients for ServiceDNA.
//
//	server := grpc.NewServer(sdnagrpc.ServerOptions()...)
//	conn, err := grpc.NewClient(target, sdnagrpc.DialOptions(grpc.WithTransportCredentials(...))...)
package sdnagrpc

import (
	"context"
	"encoding/json"

	servicedna "github.com/varuns2903/servicedna/sdks/go"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

// ServerOptions traces every call (with the caller's trace context and baggage) and, for
// ServiceDNA test runs, records unary calls' request and response messages on the span as JSON
// (masked and size-capped). With SERVICEDNA_CAPTURE_ON_ERROR=true, failed calls' requests are
// recorded too.
func ServerOptions(opts ...grpc.ServerOption) []grpc.ServerOption {
	return append([]grpc.ServerOption{
		grpc.StatsHandler(otelgrpc.NewServerHandler()),
		grpc.ChainUnaryInterceptor(UnaryServerInterceptor()),
	}, opts...)
}

// DialOptions traces outgoing calls and propagates trace context and baggage to the services
// they call.
func DialOptions(opts ...grpc.DialOption) []grpc.DialOption {
	return append([]grpc.DialOption{grpc.WithStatsHandler(otelgrpc.NewClientHandler())}, opts...)
}

// UnaryServerInterceptor records messages as ServerOptions describes; it must run inside the
// otelgrpc server handler (ServerOptions puts it there).
func UnaryServerInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp any, err error) {
		requested := servicedna.CaptureRequested(ctx)
		if !requested && !servicedna.CaptureOnError() {
			return handler(ctx, req)
		}
		span := trace.SpanFromContext(ctx)
		defer func() {
			if panicked := recover(); panicked != nil {
				record(span, req, nil, requested)
				panic(panicked)
			}
		}()
		resp, err = handler(ctx, req)
		if err != nil {
			record(span, req, nil, requested)
		} else if requested {
			record(span, req, resp, requested)
		}
		return resp, err
	}
}

func record(span trace.Span, req, resp any, requested bool) {
	if !span.IsRecording() {
		return
	}
	span.SetAttributes(attribute.String("sdna.request.body", servicedna.Redact(toJSON(req))))
	if resp != nil {
		span.SetAttributes(attribute.String("sdna.response.body", servicedna.Redact(toJSON(resp))))
	}
	if requested {
		span.SetAttributes(attribute.Bool("sdna.captured", true))
	} else {
		span.SetAttributes(attribute.Bool("sdna.captured_on_error", true))
	}
}

func toJSON(message any) string {
	if m, ok := message.(proto.Message); ok {
		if b, err := (protojson.MarshalOptions{UseProtoNames: true}).Marshal(m); err == nil {
			return string(b)
		}
	}
	b, err := json.Marshal(message)
	if err != nil {
		return ""
	}
	return string(b)
}
