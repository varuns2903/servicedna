package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

func (r *Runner) executeHTTP(ctx context.Context, job Job) Result {
	base := r.cfg.Targets[job.Target.Service]
	if base == "" {
		base = job.Target.BaseURL
	}
	if base == "" {
		return failed(fmt.Errorf("don't know where %s is: give it a health-check URL in ServiceDNA, or set RUNNER_TARGETS=%s=http://host:port", job.Target.Service, job.Target.Service))
	}
	req, err := http.NewRequestWithContext(ctx, job.Target.Method, strings.TrimRight(base, "/")+job.Target.Path, strings.NewReader(job.Request.Body))
	if err != nil {
		return failed(err)
	}
	if job.Request.Body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range job.Request.Headers {
		req.Header.Set(k, v)
	}
	for k, v := range job.propagation() {
		req.Header.Set(k, v)
	}

	start := time.Now()
	res, err := r.http.Do(req)
	if err != nil {
		return failed(err)
	}
	defer res.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(res.Body, maxBody+1))
	headers := map[string]string{}
	for k := range res.Header {
		headers[strings.ToLower(k)] = res.Header.Get(k)
	}
	status := res.StatusCode
	return Result{Sent: true, Status: &status, Headers: headers, Body: truncate(body), DurationMs: time.Since(start).Milliseconds()}
}
