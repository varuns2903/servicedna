package sdnahttp

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	servicedna "github.com/varuns2903/servicedna/sdks/go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
)

func setup(t *testing.T) *tracetest.SpanRecorder {
	t.Helper()
	recorder := tracetest.NewSpanRecorder()
	otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(recorder)))
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))
	return recorder
}

func handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /reserve", func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		var req map[string]any
		_ = json.Unmarshal(body, &req)
		switch req["fail"] {
		case "503":
			http.Error(w, `{"error":"no stock service"}`, http.StatusServiceUnavailable)
			return
		case "409":
			http.Error(w, `{"error":"out of stock"}`, http.StatusConflict)
			return
		case "panic":
			panic("bug")
		}
		servicedna.Capture(r.Context(), "remaining", 119)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"sku": req["sku"], "sessionToken": "t"})
	})
	return Handler(mux)
}

func attrs(recorder *tracetest.SpanRecorder) map[string]string {
	out := map[string]string{}
	for _, s := range recorder.Ended() {
		for _, a := range s.Attributes() {
			out[string(a.Key)] = a.Value.Emit()
		}
	}
	return out
}

func TestCapturesBodiesOfACaptureRun(t *testing.T) {
	recorder := setup(t)
	req := httptest.NewRequest(http.MethodPost, "/reserve", strings.NewReader(`{"sku":"KB-001","password":"x"}`))
	req.Header.Set("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
	req.Header.Set("baggage", "sdna.run=run_1,sdna.capture=1")
	res := httptest.NewRecorder()
	handler().ServeHTTP(res, req)

	if !strings.Contains(res.Body.String(), `"sku":"KB-001"`) {
		t.Fatalf("application didn't get the full body: %s", res.Body.String())
	}
	a := attrs(recorder)
	if a["sdna.request.body"] != `{"password":"[masked]","sku":"KB-001"}` {
		t.Fatalf("request body: %q", a["sdna.request.body"])
	}
	if a["sdna.response.body"] != `{"sessionToken":"[masked]","sku":"KB-001"}` {
		t.Fatalf("response body: %q", a["sdna.response.body"])
	}
	if a["sdna.capture.remaining"] != "119" || a["sdna.captured"] != "true" {
		t.Fatalf("attributes: %v", a)
	}
}

func TestLeavesOrdinaryRequestsAlone(t *testing.T) {
	recorder := setup(t)
	req := httptest.NewRequest(http.MethodPost, "/reserve", strings.NewReader(`{"sku":"KB-001"}`))
	handler().ServeHTTP(httptest.NewRecorder(), req)
	a := attrs(recorder)
	for _, key := range []string{"sdna.request.body", "sdna.response.body", "sdna.capture.remaining"} {
		if _, ok := a[key]; ok {
			t.Fatalf("%s recorded outside a capture run", key)
		}
	}
}

func TestWithCaptureOnErrorOnlyFailedRequestsCarryTheirBodies(t *testing.T) {
	t.Setenv("SERVICEDNA_CAPTURE_ON_ERROR", "true")
	send := func(body string) map[string]string {
		recorder := setup(t)
		func() {
			defer func() { recover() }()
			handler().ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodPost, "/reserve", strings.NewReader(body)))
		}()
		return attrs(recorder)
	}

	for _, body := range []string{`{"sku":"KB-001"}`, `{"sku":"KB-001","fail":"409"}`} {
		if a := send(body); a["sdna.request.body"] != "" || a["sdna.captured_on_error"] != "" {
			t.Fatalf("%s: recorded %v", body, a)
		}
	}
	a := send(`{"sku":"KB-001","fail":"503","password":"x"}`)
	if a["sdna.request.body"] != `{"fail":"503","password":"[masked]","sku":"KB-001"}` || a["sdna.captured_on_error"] != "true" {
		t.Fatalf("503: %v", a)
	}
	if !strings.Contains(a["sdna.response.body"], "no stock service") || a["sdna.captured"] != "" {
		t.Fatalf("503 response: %v", a)
	}
	if a := send(`{"sku":"KB-001","fail":"panic"}`); a["sdna.request.body"] == "" || a["sdna.captured_on_error"] != "true" {
		t.Fatalf("panic: %v", a)
	}
}

func TestTagRecordsABusinessKeyOnAnyRequest(t *testing.T) {
	recorder := setup(t)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /orders/{id}", func(w http.ResponseWriter, r *http.Request) {
		servicedna.Tag(r.Context(), "orderId", r.PathValue("id"))
	})
	Handler(mux).ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/orders/o-17", nil))
	if a := attrs(recorder); a["sdna.key.orderId"] != "o-17" {
		t.Fatalf("attributes: %v", a)
	}
}
