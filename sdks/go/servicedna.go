// Package servicedna connects a Go service to ServiceDNA: traces, self-registration and health
// heartbeats, configured with two environment variables.
//
//	shutdown, err := servicedna.Start(ctx) // reads SERVICEDNA_URL, SERVICEDNA_KEY, ...
//	defer shutdown(context.Background())
//	http.ListenAndServe(":8080", sdnahttp.Handler(mux))
//
// Without SERVICEDNA_URL and SERVICEDNA_KEY, Start does nothing and returns a no-op shutdown, so
// the same build runs anywhere.
package servicedna

import (
	"context"
	"log"
	"net/url"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

// ShutdownFunc flushes pending spans and stops heartbeats.
type ShutdownFunc func(context.Context) error

var current Config

// Current returns the configuration Start was called with (zero until then).
func Current() Config { return current }

// Start configures OpenTelemetry to send traces to ServiceDNA and starts heartbeats.
func Start(ctx context.Context) (ShutdownFunc, error) {
	return StartWithConfig(ctx, Load())
}

// StartWithConfig is Start with explicit settings.
func StartWithConfig(ctx context.Context, cfg Config) (ShutdownFunc, error) {
	current = cfg
	if !cfg.Enabled() {
		log.Print("[servicedna] SERVICEDNA_URL and SERVICEDNA_KEY are not set; not sending telemetry")
		return func(context.Context) error { return nil }, nil
	}
	endpoint, err := url.Parse(cfg.URL)
	if err != nil {
		return nil, err
	}

	options := []otlptracehttp.Option{
		otlptracehttp.WithEndpoint(endpoint.Host),
		otlptracehttp.WithURLPath(endpoint.Path + "/api/v1/otlp/v1/traces"),
		otlptracehttp.WithHeaders(map[string]string{"x-servicedna-key": cfg.Key}),
	}
	if endpoint.Scheme == "http" {
		options = append(options, otlptracehttp.WithInsecure())
	}
	exporter, err := otlptracehttp.New(ctx, options...)
	if err != nil {
		return nil, err
	}

	attrs := []attribute.KeyValue{
		attribute.String("service.name", cfg.ServiceName),
		attribute.String("telemetry.sdk.language", "go"),
	}
	if cfg.Environment != "" {
		attrs = append(attrs, attribute.String("deployment.environment.name", cfg.Environment))
	}
	if cfg.Version != "" {
		attrs = append(attrs, attribute.String("service.version", cfg.Version))
	}
	if cfg.HealthURL != "" {
		attrs = append(attrs, attribute.String("servicedna.health.url", cfg.HealthURL))
	}

	provider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(resource.NewSchemaless(attrs...)),
	)
	otel.SetTracerProvider(provider)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	stopHeartbeat := startHeartbeat(cfg)
	suffix := ""
	if cfg.Environment != "" {
		suffix = " (" + cfg.Environment + ")"
	}
	log.Printf("[servicedna] sending %s%s telemetry to %s", cfg.ServiceName, suffix, cfg.URL)

	return func(ctx context.Context) error {
		stopHeartbeat()
		return provider.Shutdown(ctx)
	}, nil
}
