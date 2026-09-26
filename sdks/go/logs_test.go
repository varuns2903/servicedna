package servicedna

import (
	"context"
	"encoding/hex"
	"io"
	"log"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"

	"go.opentelemetry.io/otel"
	collogs "go.opentelemetry.io/proto/otlp/collector/logs/v1"
	"google.golang.org/protobuf/proto"
)

func TestLogsGoToServiceDNAWithTheirTrace(t *testing.T) {
	var mu sync.Mutex
	var records []map[string]string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/otlp/v1/logs" || r.Header.Get("x-servicedna-key") != "sdna_ik_x" {
			w.WriteHeader(http.StatusOK)
			return
		}
		body, _ := io.ReadAll(r.Body)
		var req collogs.ExportLogsServiceRequest
		if err := proto.Unmarshal(body, &req); err != nil {
			t.Errorf("not OTLP logs: %v", err)
		}
		mu.Lock()
		defer mu.Unlock()
		for _, rl := range req.ResourceLogs {
			for _, sl := range rl.ScopeLogs {
				for _, lr := range sl.LogRecords {
					records = append(records, map[string]string{
						"body": lr.Body.GetStringValue(), "severity": lr.SeverityText,
						"trace": hex.EncodeToString(lr.TraceId), "scope": sl.Scope.GetName(),
					})
				}
			}
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	prevSlog, prevLog := slog.Default(), log.Writer()
	defer func() { slog.SetDefault(prevSlog); log.SetOutput(prevLog) }()
	shutdown, err := StartWithConfig(context.Background(), Config{URL: server.URL, Key: "sdna_ik_x", ServiceName: "inventory", Logs: true})
	if err != nil {
		t.Fatal(err)
	}
	ctx, span := otel.Tracer("test").Start(context.Background(), "POST /reserve")
	slog.ErrorContext(ctx, "out of stock", "sku", "KB-001")
	span.End()
	log.Print("plain log line")
	if err := shutdown(context.Background()); err != nil {
		t.Fatal(err)
	}

	mu.Lock()
	defer mu.Unlock()
	byBody := map[string]map[string]string{}
	for _, r := range records {
		byBody[r["body"]] = r
	}
	slogRecord, ok := byBody["out of stock"]
	if !ok || slogRecord["severity"] != "ERROR" || slogRecord["trace"] != span.SpanContext().TraceID().String() {
		t.Fatalf("slog record: %v (all: %v)", slogRecord, records)
	}
	if byBody["plain log line"] == nil {
		t.Fatalf("log package line missing: %v", records)
	}
}

func TestLogsCanBeTurnedOff(t *testing.T) {
	env := map[string]string{"SERVICEDNA_LOGS": "false"}
	if load(func(k string) string { return env[k] }).Logs {
		t.Fatal("SERVICEDNA_LOGS=false should turn logs off")
	}
	if !load(func(string) string { return "" }).Logs {
		t.Fatal("logs are on by default")
	}
}
