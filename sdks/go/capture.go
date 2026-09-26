package servicedna

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strconv"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/baggage"
	"go.opentelemetry.io/otel/trace"
)

// MaxCaptureBytes caps each captured body (SERVICEDNA_CAPTURE_MAX_BYTES, default 16 KiB).
var MaxCaptureBytes = func() int {
	if n, err := strconv.Atoi(os.Getenv("SERVICEDNA_CAPTURE_MAX_BYTES")); err == nil && n > 0 {
		return n
	}
	return 16384
}()

var sensitive = regexp.MustCompile(`(?i)pass(word|wd)?|secret|token|api[-_.]?key|authorization|cookie|session|card|cvv|ssn`)

// CaptureRequested reports whether ctx belongs to a ServiceDNA capture run (baggage sdna.capture=1).
func CaptureRequested(ctx context.Context) bool {
	return baggage.FromContext(ctx).Member("sdna.capture").Value() == "1"
}

// Capture records a value computed inside the service on the current span, for test runs:
//
//	servicedna.Capture(ctx, "order.total", total)
//
// It does nothing outside a capture run.
func Capture(ctx context.Context, name string, value any) {
	if !CaptureRequested(ctx) {
		return
	}
	span := trace.SpanFromContext(ctx)
	if !span.IsRecording() {
		return
	}
	text, ok := value.(string)
	if !ok {
		data, err := json.Marshal(value)
		if err != nil {
			text = fmt.Sprint(value)
		} else {
			text = string(data)
		}
	}
	span.SetAttributes(attribute.String("sdna.capture."+name, Redact(text)))
}

// Redact masks credential-like fields in JSON (other text is kept) and truncates to MaxCaptureBytes.
func Redact(text string) string {
	var parsed any
	if json.Unmarshal([]byte(text), &parsed) == nil {
		if data, err := json.Marshal(mask(parsed)); err == nil {
			text = string(data)
		}
	}
	if len(text) > MaxCaptureBytes {
		return text[:MaxCaptureBytes] + "…[truncated]"
	}
	return text
}

func mask(v any) any {
	switch t := v.(type) {
	case map[string]any:
		out := make(map[string]any, len(t))
		for k, val := range t {
			if sensitive.MatchString(k) {
				out[k] = "[masked]"
			} else {
				out[k] = mask(val)
			}
		}
		return out
	case []any:
		for i := range t {
			t[i] = mask(t[i])
		}
		return t
	}
	return v
}
