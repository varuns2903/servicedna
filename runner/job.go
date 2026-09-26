package main

import (
	"crypto/rand"
	"encoding/hex"
)

// Job is a test run claimed from ServiceDNA.
type Job struct {
	ID             string `json:"id"`
	OrganizationID string `json:"organizationId"`
	Protocol       string `json:"protocol"`
	Target         struct {
		Service string `json:"service"`
		Method  string `json:"method"`
		Path    string `json:"path"`
		BaseURL string `json:"baseUrl"`
		Address string `json:"address"`
		Topic   string `json:"topic"`
	} `json:"target"`
	Request struct {
		Headers map[string]string `json:"headers"`
		Body    string            `json:"body"`
		Key     string            `json:"key"`
		Test    bool              `json:"test"`
	} `json:"request"`
	TraceID string `json:"traceId"`
}

// Result is what the runner reports: the entry call's outcome.
type Result struct {
	Sent       bool              `json:"sent"`
	Error      string            `json:"error,omitempty"`
	Status     *int              `json:"status,omitempty"`
	Headers    map[string]string `json:"headers,omitempty"`
	Body       string            `json:"body,omitempty"`
	DurationMs int64             `json:"durationMs"`
	Partition  *int32            `json:"partition,omitempty"`
	Offset     *int64            `json:"offset,omitempty"`
}

func failed(err error) Result { return Result{Sent: false, Error: err.Error()} }

// propagation is the W3C context every request carries, so the services' spans join the run's
// trace and their SDKs capture bodies (sdna.capture=1).
func (j Job) propagation() map[string]string {
	span := make([]byte, 8)
	_, _ = rand.Read(span)
	baggage := "sdna.run=" + j.ID + ",sdna.capture=1"
	if j.Request.Test {
		baggage += ",sdna.test=1"
	}
	return map[string]string{
		"traceparent": "00-" + j.TraceID + "-" + hex.EncodeToString(span) + "-01",
		"baggage":     baggage,
	}
}

const maxBody = 64 * 1024

func truncate(b []byte) string {
	if len(b) > maxBody {
		return string(b[:maxBody]) + "…[truncated]"
	}
	return string(b)
}

