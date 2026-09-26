package main

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"

	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/reflection"
)

func testJob() Job {
	var j Job
	j.ID = "run-1"
	j.TraceID = "4bf92f3577b34da6a3ce929d0e0e4736"
	return j
}

func TestHTTPCarriesTraceContextAndCaptureBaggage(t *testing.T) {
	var got http.Header
	var body string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r.Header.Clone()
		b := make([]byte, 100)
		n, _ := r.Body.Read(b)
		body = string(b[:n])
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(201)
		w.Write([]byte(`{"id":"o-1"}`))
	}))
	defer srv.Close()

	job := testJob()
	job.Protocol = "HTTP"
	job.Target.Service, job.Target.Method, job.Target.Path = "orders", "POST", "/orders"
	job.Target.BaseURL = "http://unreachable.invalid"
	job.Request.Body = `{"userId":"u-1"}`
	job.Request.Headers = map[string]string{"x-tenant": "t1"}
	r := &Runner{cfg: Config{Targets: map[string]string{"orders": srv.URL}}, http: srv.Client()}

	res := r.execute(context.Background(), job)
	if !res.Sent || *res.Status != 201 || res.Body != `{"id":"o-1"}` || res.Headers["content-type"] != "application/json" {
		t.Fatalf("result %+v", res)
	}
	if !regexp.MustCompile(`^00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01$`).MatchString(got.Get("traceparent")) {
		t.Fatalf("traceparent %q", got.Get("traceparent"))
	}
	if got.Get("baggage") != "sdna.run=run-1,sdna.capture=1" || got.Get("x-tenant") != "t1" || body != `{"userId":"u-1"}` {
		t.Fatalf("headers %v body %q", got, body)
	}
}

func TestAllowListsAreEnforced(t *testing.T) {
	r := &Runner{cfg: Config{AllowedServices: []string{"checkout-*"}, AllowedTopics: []string{"order.*"}}}
	job := testJob()
	job.Protocol, job.Target.Service = "HTTP", "payments"
	if res := r.execute(context.Background(), job); res.Sent || !strings.Contains(res.Error, "RUNNER_ALLOWED_SERVICES") {
		t.Fatalf("service not blocked: %+v", res)
	}
	job.Protocol, job.Target.Topic = "MESSAGING", "billing.charged"
	if res := r.execute(context.Background(), job); res.Sent || !strings.Contains(res.Error, "RUNNER_ALLOWED_TOPICS") {
		t.Fatalf("topic not blocked: %+v", res)
	}
}

func TestGRPCCallsAMethodDiscoveredByReflection(t *testing.T) {
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	var md metadata.MD
	srv := grpc.NewServer(grpc.UnaryInterceptor(func(ctx context.Context, req any, info *grpc.UnaryServerInfo, h grpc.UnaryHandler) (any, error) {
		md, _ = metadata.FromIncomingContext(ctx)
		return h(ctx, req)
	}))
	healthpb.RegisterHealthServer(srv, health.NewServer())
	reflection.Register(srv)
	go srv.Serve(lis)
	defer srv.Stop()

	job := testJob()
	job.Protocol = "GRPC"
	job.Target.Service, job.Target.Method, job.Target.Address = "health", "grpc.health.v1.Health/Check", lis.Addr().String()
	job.Request.Body = `{}`
	res := (&Runner{}).execute(context.Background(), job)

	if !res.Sent || *res.Status != 0 || res.Body != `{"status":"SERVING"}` {
		t.Fatalf("result %+v", res)
	}
	if len(md.Get("traceparent")) != 1 || md.Get("baggage")[0] != "sdna.run=run-1,sdna.capture=1" {
		t.Fatalf("metadata %v", md)
	}

	job.Target.Method = "grpc.health.v1.Health/Nope"
	if res := (&Runner{}).execute(context.Background(), job); res.Sent || !strings.Contains(res.Error, "has no method Nope") {
		t.Fatalf("unknown method: %+v", res)
	}
}
