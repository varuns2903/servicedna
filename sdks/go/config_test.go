package servicedna

import (
	"testing"
	"time"
)

func env(values map[string]string) func(string) string {
	return func(key string) string { return values[key] }
}

func TestDisabledWithoutURLAndKey(t *testing.T) {
	if load(env(nil)).Enabled() {
		t.Fatal("expected disabled")
	}
	if load(env(map[string]string{"SERVICEDNA_URL": "http://x"})).Enabled() {
		t.Fatal("expected disabled without key")
	}
}

func TestTwoVariablesAreEnough(t *testing.T) {
	c := load(env(map[string]string{"SERVICEDNA_URL": "http://sdna:8080/", "SERVICEDNA_KEY": "sdna_ik_x", "OTEL_SERVICE_NAME": "inventory"}))
	if !c.Enabled() || c.URL != "http://sdna:8080" || c.ServiceName != "inventory" {
		t.Fatalf("unexpected config: %+v", c)
	}
	if c.Heartbeat != 15*time.Second || c.DegradedLatency != 2*time.Second {
		t.Fatalf("unexpected defaults: %+v", c)
	}
}

func TestHeartbeatChecksHealthOnPort(t *testing.T) {
	if got := load(env(map[string]string{"PORT": "4003"})).LocalHealthURL; got != "http://127.0.0.1:4003/health" {
		t.Fatalf("got %q", got)
	}
	if got := load(env(map[string]string{"PORT": "8080", "SERVICEDNA_HEALTH_PATH": "/healthz"})).LocalHealthURL; got != "http://127.0.0.1:8080/healthz" {
		t.Fatalf("got %q", got)
	}
}
