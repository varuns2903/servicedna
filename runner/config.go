package main

import (
	"os"
	"path"
	"strings"
)

// Config comes from environment variables; see README.md.
type Config struct {
	URL         string
	Key         string // organization ingestion key (runner in a customer network)
	SharedToken string // RUNNER_SHARED_TOKEN (runner bundled with self-hosted ServiceDNA)
	Environment string
	Name        string

	// Overrides for where services are, when ServiceDNA's idea (their health-check URL) isn't
	// reachable from the runner: "service=http://host:port,..." and "service=host:port,...".
	Targets     map[string]string
	GRPCTargets map[string]string

	KafkaBrokers  []string
	KafkaSASL     string // PLAIN, SCRAM-SHA-256, SCRAM-SHA-512
	KafkaUser     string
	KafkaPassword string
	KafkaTLS      bool

	// Glob patterns; empty allows everything.
	AllowedServices []string
	AllowedTopics   []string
}

func loadConfig(getenv func(string) string) Config {
	name := getenv("RUNNER_NAME")
	if name == "" {
		name, _ = os.Hostname()
	}
	return Config{
		URL:             strings.TrimRight(getenv("SERVICEDNA_URL"), "/"),
		Key:             getenv("SERVICEDNA_KEY"),
		SharedToken:     getenv("RUNNER_SHARED_TOKEN"),
		Environment:     getenv("SERVICEDNA_ENV"),
		Name:            name,
		Targets:         pairs(getenv("RUNNER_TARGETS")),
		GRPCTargets:     pairs(getenv("RUNNER_GRPC_TARGETS")),
		KafkaBrokers:    list(getenv("KAFKA_BROKERS")),
		KafkaSASL:       strings.ToUpper(getenv("KAFKA_SASL_MECHANISM")),
		KafkaUser:       getenv("KAFKA_SASL_USERNAME"),
		KafkaPassword:   getenv("KAFKA_SASL_PASSWORD"),
		KafkaTLS:        getenv("KAFKA_TLS") == "true",
		AllowedServices: list(getenv("RUNNER_ALLOWED_SERVICES")),
		AllowedTopics:   list(getenv("RUNNER_ALLOWED_TOPICS")),
	}
}

func list(v string) []string {
	var out []string
	for _, s := range strings.Split(v, ",") {
		if s = strings.TrimSpace(s); s != "" {
			out = append(out, s)
		}
	}
	return out
}

func pairs(v string) map[string]string {
	out := map[string]string{}
	for _, item := range list(v) {
		if k, val, ok := strings.Cut(item, "="); ok {
			out[strings.TrimSpace(k)] = strings.TrimSpace(val)
		}
	}
	return out
}

func allowed(patterns []string, name string) bool {
	if len(patterns) == 0 {
		return true
	}
	for _, p := range patterns {
		if ok, _ := path.Match(p, name); ok {
			return true
		}
	}
	return false
}
