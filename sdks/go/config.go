package servicedna

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// Config holds the SDK settings. Load reads them from environment variables; only SERVICEDNA_URL
// and SERVICEDNA_KEY are required.
type Config struct {
	URL         string
	Key         string
	ServiceName string
	Environment string
	Version     string
	// HealthURL is what ServiceDNA probes; only reported when set, since a service can't know the
	// address ServiceDNA reaches it on.
	HealthURL string
	// LocalHealthURL is what the heartbeat checks, over loopback.
	LocalHealthURL  string
	Heartbeat       time.Duration
	DegradedLatency time.Duration
	// Logs sends slog and log package output to ServiceDNA too (SERVICEDNA_LOGS=false turns it off).
	Logs bool
}

// Enabled reports whether telemetry will be sent.
func (c Config) Enabled() bool { return c.URL != "" && c.Key != "" }

// Load reads the configuration from the environment.
func Load() Config { return load(os.Getenv) }

func load(getenv func(string) string) Config {
	get := func(key, fallback string) string {
		if v := getenv(key); v != "" {
			return v
		}
		return fallback
	}
	c := Config{
		URL:             strings.TrimRight(getenv("SERVICEDNA_URL"), "/"),
		Key:             getenv("SERVICEDNA_KEY"),
		ServiceName:     get("OTEL_SERVICE_NAME", get("SERVICEDNA_SERVICE", executableName())),
		Environment:     getenv("SERVICEDNA_ENV"),
		Version:         getenv("SERVICEDNA_VERSION"),
		HealthURL:       getenv("SERVICEDNA_HEALTH_URL"),
		LocalHealthURL:  getenv("SERVICEDNA_LOCAL_HEALTH_URL"),
		Heartbeat:       millis(get("SERVICEDNA_HEARTBEAT_MS", "15000")),
		DegradedLatency: millis(get("SERVICEDNA_DEGRADED_MS", "2000")),
		Logs:            !isFalse(getenv("SERVICEDNA_LOGS")),
	}
	if c.LocalHealthURL == "" {
		if port := getenv("PORT"); port != "" {
			c.LocalHealthURL = "http://127.0.0.1:" + port + get("SERVICEDNA_HEALTH_PATH", "/health")
		}
	}
	return c
}

func isFalse(v string) bool {
	switch strings.ToLower(v) {
	case "0", "false", "no", "off":
		return true
	}
	return false
}

func millis(v string) time.Duration {
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0
	}
	return time.Duration(n) * time.Millisecond
}

func executableName() string {
	exe, err := os.Executable()
	if err != nil {
		return "unknown_service:go"
	}
	return filepath.Base(exe)
}
