package servicedna

import (
	"bytes"
	"context"
	"io"
	"log"
	"log/slog"
	"net/url"
	"os"

	"go.opentelemetry.io/contrib/bridges/otelslog"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp"
	otellog "go.opentelemetry.io/otel/log"
	"go.opentelemetry.io/otel/log/global"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	"go.opentelemetry.io/otel/sdk/resource"
)

// startLogs sends the service's logs to ServiceDNA: slog's default logger (records logged with a
// context carry its trace and span ids) and the log package, whose output looks as it did.
func startLogs(ctx context.Context, cfg Config, endpoint *url.URL, res *resource.Resource) (func(context.Context) error, error) {
	options := []otlploghttp.Option{
		otlploghttp.WithEndpoint(endpoint.Host),
		otlploghttp.WithURLPath(endpoint.Path + "/api/v1/otlp/v1/logs"),
		otlploghttp.WithHeaders(map[string]string{"x-servicedna-key": cfg.Key}),
	}
	if endpoint.Scheme == "http" {
		options = append(options, otlploghttp.WithInsecure())
	}
	exporter, err := otlploghttp.New(ctx, options...)
	if err != nil {
		return nil, err
	}
	provider := sdklog.NewLoggerProvider(sdklog.WithProcessor(sdklog.NewBatchProcessor(exporter)), sdklog.WithResource(res))
	global.SetLoggerProvider(provider)

	bridge := otelslog.NewHandler("slog", otelslog.WithLoggerProvider(provider))
	slog.SetDefault(slog.New(fanout{slog.NewTextHandler(os.Stderr, nil), bridge}))
	// slog.SetDefault routes the log package through slog; route it to stderr as before instead,
	// plus ServiceDNA.
	log.SetOutput(io.MultiWriter(os.Stderr, lineWriter{provider.Logger("log")}))
	return provider.Shutdown, nil
}

// fanout sends each record to every handler that wants it.
type fanout []slog.Handler

func (f fanout) Enabled(ctx context.Context, level slog.Level) bool {
	for _, h := range f {
		if h.Enabled(ctx, level) {
			return true
		}
	}
	return false
}

func (f fanout) Handle(ctx context.Context, r slog.Record) error {
	var first error
	for _, h := range f {
		if h.Enabled(ctx, r.Level) {
			if err := h.Handle(ctx, r.Clone()); err != nil && first == nil {
				first = err
			}
		}
	}
	return first
}

func (f fanout) WithAttrs(attrs []slog.Attr) slog.Handler {
	out := make(fanout, len(f))
	for i, h := range f {
		out[i] = h.WithAttrs(attrs)
	}
	return out
}

func (f fanout) WithGroup(name string) slog.Handler {
	out := make(fanout, len(f))
	for i, h := range f {
		out[i] = h.WithGroup(name)
	}
	return out
}

// lineWriter turns log package output (one write per entry) into log records.
type lineWriter struct{ logger otellog.Logger }

func (w lineWriter) Write(p []byte) (int, error) {
	var r otellog.Record
	r.SetSeverity(otellog.SeverityInfo)
	r.SetSeverityText("INFO")
	r.SetBody(attribute.StringValue(string(bytes.TrimRight(p, "\n"))))
	w.logger.Emit(context.Background(), r)
	return len(p), nil
}
