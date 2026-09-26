package servicedna

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"
)

// startHeartbeat checks the service's own health endpoint on an interval and reports the result to
// ServiceDNA, which registers the service on first contact. It uses a plain HTTP client, so
// heartbeats aren't traced as the service's traffic.
func startHeartbeat(cfg Config) (stop func()) {
	if cfg.LocalHealthURL == "" || cfg.Heartbeat <= 0 {
		return func() {}
	}
	client := &http.Client{Timeout: 5 * time.Second}
	done := make(chan struct{})
	go func() {
		ticker := time.NewTicker(cfg.Heartbeat)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				beat(client, cfg)
			}
		}
	}()
	return func() { close(done) }
}

func beat(client *http.Client, cfg Config) {
	start := time.Now()
	status, message := "DOWN", "health check failed"
	if res, err := client.Get(cfg.LocalHealthURL); err != nil {
		message = err.Error()
	} else {
		var body struct {
			Message string `json:"message"`
			Status  string `json:"status"`
		}
		_ = json.NewDecoder(res.Body).Decode(&body)
		res.Body.Close()
		message = firstNonEmpty(body.Message, body.Status, fmt.Sprintf("HTTP %d", res.StatusCode))
		if res.StatusCode < 300 {
			status = "HEALTHY"
			if time.Since(start) > cfg.DegradedLatency {
				status = "DEGRADED"
			}
		}
	}

	payload, _ := json.Marshal(map[string]any{
		"status":      status,
		"latencyMs":   time.Since(start).Milliseconds(),
		"message":     truncate(message, 500),
		"service":     cfg.ServiceName,
		"environment": cfg.Environment,
	})
	req, err := http.NewRequest(http.MethodPost, cfg.URL+"/api/v1/ping", bytes.NewReader(payload))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", cfg.Key)
	res, err := client.Do(req)
	if err != nil {
		log.Printf("[servicedna] heartbeat failed: %v", err)
		return
	}
	res.Body.Close()
	if res.StatusCode >= 300 {
		log.Printf("[servicedna] heartbeat rejected: HTTP %d", res.StatusCode)
	}
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max]
}
