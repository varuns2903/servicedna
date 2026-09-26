package sdnahttp

import (
	"bytes"
	"io"
	"net/http"

	servicedna "github.com/varuns2903/servicedna/sdks/go"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
)

// captureBodies records request and response bodies on the server span for ServiceDNA capture runs
// (baggage sdna.capture=1) and, with servicedna.CaptureOnError, for requests that fail (5xx or a
// panic). Runs inside the otelhttp handler, where trace context and baggage have been extracted.
func captureBodies(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requested := servicedna.CaptureRequested(r.Context())
		if !requested && !servicedna.CaptureOnError() {
			next.ServeHTTP(w, r)
			return
		}
		var request []byte
		if r.Body != nil {
			request, _ = io.ReadAll(io.LimitReader(r.Body, int64(servicedna.MaxCaptureBytes)+1))
			// The application still reads the whole body: what was read, then the rest.
			r.Body = struct {
				io.Reader
				io.Closer
			}{io.MultiReader(bytes.NewReader(request), r.Body), r.Body}
		}
		rec := &bodyRecorder{ResponseWriter: w}
		span := trace.SpanFromContext(r.Context())
		if !requested {
			defer func() {
				panicked := recover()
				if panicked != nil || rec.status >= 500 {
					record(span, request, rec)
					span.SetAttributes(attribute.Bool("sdna.captured_on_error", true))
				}
				if panicked != nil {
					panic(panicked)
				}
			}()
			next.ServeHTTP(rec, r)
			return
		}
		next.ServeHTTP(rec, r)
		record(span, request, rec)
		span.SetAttributes(attribute.Bool("sdna.captured", true))
	})
}

func record(span trace.Span, request []byte, rec *bodyRecorder) {
	if len(request) > 0 {
		span.SetAttributes(attribute.String("sdna.request.body", servicedna.Redact(string(request))))
	}
	if rec.body.Len() > 0 {
		span.SetAttributes(attribute.String("sdna.response.body", servicedna.Redact(rec.body.String())))
	}
}

type bodyRecorder struct {
	http.ResponseWriter
	body   bytes.Buffer
	status int
}

func (b *bodyRecorder) WriteHeader(status int) {
	if b.status == 0 {
		b.status = status
	}
	b.ResponseWriter.WriteHeader(status)
}

func (b *bodyRecorder) Write(p []byte) (int, error) {
	if b.status == 0 {
		b.status = http.StatusOK
	}
	if b.body.Len() <= servicedna.MaxCaptureBytes {
		b.body.Write(p)
	}
	return b.ResponseWriter.Write(p)
}

func (b *bodyRecorder) Flush() {
	if f, ok := b.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

// Unwrap lets http.ResponseController reach the underlying writer.
func (b *bodyRecorder) Unwrap() http.ResponseWriter { return b.ResponseWriter }
