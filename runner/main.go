// The ServiceDNA runner sends Test Studio requests from inside your network: it claims runs from
// ServiceDNA over an outbound connection, sends them (HTTP, GraphQL, gRPC, Kafka) with trace
// context so the services' spans join the run, and reports the entry call's result.
package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

type Runner struct {
	cfg   Config
	api   *http.Client
	http  *http.Client
	kafka kafkaProducer
}

func main() {
	cfg := loadConfig(os.Getenv)
	if cfg.URL == "" || (cfg.Key == "" && cfg.SharedToken == "") {
		log.Fatal("set SERVICEDNA_URL and SERVICEDNA_KEY (or RUNNER_SHARED_TOKEN for the runner bundled with ServiceDNA)")
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	r := &Runner{
		cfg:  cfg,
		api:  &http.Client{Timeout: 40 * time.Second},
		http: &http.Client{Timeout: 30 * time.Second},
	}
	env := cfg.Environment
	if env == "" {
		env = "any environment"
	}
	log.Printf("[runner] %s serving %s from %s", cfg.Name, env, cfg.URL)
	r.loop(ctx)
}

func (r *Runner) loop(ctx context.Context) {
	backoff := time.Second
	for ctx.Err() == nil {
		job, err := r.claim(ctx)
		if err != nil {
			log.Printf("[runner] claim failed: %v (retrying in %s)", err, backoff)
			select {
			case <-ctx.Done():
			case <-time.After(backoff):
			}
			backoff = min(backoff*2, 30*time.Second)
			continue
		}
		backoff = time.Second
		if job == nil {
			continue
		}
		result := r.execute(ctx, *job)
		if err := r.report(ctx, job.ID, result); err != nil {
			log.Printf("[runner] reporting run %s failed: %v", job.ID, err)
		}
	}
}

func (r *Runner) execute(ctx context.Context, job Job) Result {
	log.Printf("[runner] run %s: %s %s%s%s", job.ID, job.Protocol, job.Target.Service, job.Target.Path, job.Target.Topic)
	switch {
	case job.Protocol == "MESSAGING" && !allowed(r.cfg.AllowedTopics, job.Target.Topic):
		return failed(fmt.Errorf("topic %s isn't in this runner's RUNNER_ALLOWED_TOPICS", job.Target.Topic))
	case job.Protocol != "MESSAGING" && !allowed(r.cfg.AllowedServices, job.Target.Service):
		return failed(fmt.Errorf("service %s isn't in this runner's RUNNER_ALLOWED_SERVICES", job.Target.Service))
	}
	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	switch job.Protocol {
	case "HTTP", "GRAPHQL":
		return r.executeHTTP(ctx, job)
	case "GRPC":
		return r.executeGRPC(ctx, job)
	case "MESSAGING":
		return r.executeKafka(ctx, job)
	}
	return failed(fmt.Errorf("unsupported protocol %s", job.Protocol))
}

func (r *Runner) claim(ctx context.Context) (*Job, error) {
	body, _ := json.Marshal(map[string]string{"environment": r.cfg.Environment, "runner": r.cfg.Name})
	res, err := r.post(ctx, "/api/v1/runner/claim", body)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	switch res.StatusCode {
	case http.StatusNoContent:
		return nil, nil
	case http.StatusOK:
		var job Job
		return &job, json.NewDecoder(res.Body).Decode(&job)
	}
	return nil, fmt.Errorf("HTTP %d", res.StatusCode)
}

func (r *Runner) report(ctx context.Context, id string, result Result) error {
	body, _ := json.Marshal(result)
	res, err := r.post(ctx, "/api/v1/runner/runs/"+id+"/result", body)
	if err != nil {
		return err
	}
	res.Body.Close()
	if res.StatusCode >= 300 {
		return fmt.Errorf("HTTP %d", res.StatusCode)
	}
	return nil
}

func (r *Runner) post(ctx context.Context, path string, body []byte) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, r.cfg.URL+path, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if r.cfg.SharedToken != "" {
		req.Header.Set("X-Runner-Token", r.cfg.SharedToken)
	} else {
		req.Header.Set("x-servicedna-key", r.cfg.Key)
	}
	return r.api.Do(req)
}
