package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
)

func write(t *testing.T, dir, name, content string) {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestDetectStack(t *testing.T) {
	cases := []struct {
		files   map[string]string
		stack   string
		service string
	}{
		{map[string]string{"package.json": `{"name":"@shop/checkout"}`}, "node", "checkout"},
		{map[string]string{"go.mod": "module github.com/acme/inventory\n\ngo 1.25\n"}, "go", "inventory"},
		{map[string]string{"requirements.txt": "fastapi\n"}, "python", ""},
		{map[string]string{"pom.xml": "<project/>", "package.json": "{}", "src/main/resources/application.yml": "spring:\n  application:\n    name: quotes\n"}, "spring-boot", "quotes"},
	}
	for _, tc := range cases {
		dir := t.TempDir()
		for name, content := range tc.files {
			write(t, dir, name, content)
		}
		stack, ok := detectStack(dir)
		if !ok || stack.Name != tc.stack {
			t.Fatalf("files %v: got %+v, want %s", tc.files, stack, tc.stack)
		}
		want := tc.service
		if want == "" {
			want = filepath.Base(dir)
		}
		if stack.Service != want {
			t.Fatalf("files %v: service %q, want %q", tc.files, stack.Service, want)
		}
	}
	if _, ok := detectStack(t.TempDir()); ok {
		t.Fatal("empty directory should not be detected")
	}
}

// A request with an expired token gets a bare 403; the client refreshes once and retries.
func TestClientRefreshesAnExpiredSession(t *testing.T) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/auth/refresh":
			json.NewEncoder(w).Encode(map[string]string{"token": "fresh", "refreshToken": "r2"})
		case "/api/v1/organizations":
			calls++
			if r.Header.Get("Authorization") != "Bearer fresh" {
				w.WriteHeader(http.StatusForbidden)
				return
			}
			json.NewEncoder(w).Encode([]Organization{{ID: "o1", Name: "ShopLite"}})
		}
	}))
	defer srv.Close()
	t.Setenv("SDNA_CONFIG", filepath.Join(t.TempDir(), "config.json"))

	c := newClient(&Config{URL: srv.URL, Token: "expired", RefreshToken: "r1"})
	var orgs []Organization
	if err := c.do(http.MethodGet, "/organizations", nil, &orgs); err != nil {
		t.Fatal(err)
	}
	if len(orgs) != 1 || calls != 2 || c.cfg.Token != "fresh" || c.cfg.RefreshToken != "r2" {
		t.Fatalf("orgs=%v calls=%d cfg=%+v", orgs, calls, c.cfg)
	}
}

// Authorization failures carry an errorCode and must not trigger a refresh.
func TestClientSurfacesAPIErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		json.NewEncoder(w).Encode(map[string]string{"errorCode": "ACCESS_DENIED", "message": "Only owners and admins can manage ingestion keys"})
	}))
	defer srv.Close()
	c := newClient(&Config{URL: srv.URL, Token: "t", RefreshToken: "r"})
	err := c.do(http.MethodPost, "/organizations/o1/ingestion-keys", map[string]string{"name": "x"}, nil)
	if err == nil || !strings.Contains(err.Error(), "Only owners and admins") {
		t.Fatalf("got %v", err)
	}
}

func TestEnsureGitignored(t *testing.T) {
	dir := t.TempDir()
	write(t, dir, ".gitignore", "node_modules")
	ensureGitignored(dir, ".env.servicedna")
	ensureGitignored(dir, ".env.servicedna")
	data, _ := os.ReadFile(filepath.Join(dir, ".gitignore"))
	if string(data) != "node_modules\n.env.servicedna\n" {
		t.Fatalf("got %q", data)
	}
}

func TestScanFindsSpecsAndReferences(t *testing.T) {
	dir := t.TempDir()
	write(t, dir, "openapi.yaml", `openapi: 3.0.0
paths:
  /orders:
    post:
      summary: Place an order
      requestBody:
        content:
          application/json:
            schema: {$ref: '#/components/schemas/Order'}
  /orders/{id}:
    get: {}
components:
  schemas:
    Order: {type: object, properties: {userId: {type: string}}}
`)
	write(t, dir, "proto/payment.proto", `syntax = "proto3";
package shop.payments;
service Payment {
  rpc Charge(ChargeRequest) returns (ChargeReply);
}
message ChargeRequest {
  string order_id = 1;
  double amount = 2;
}
`)
	write(t, dir, "asyncapi.yaml", `asyncapi: 2.6.0
channels:
  order.created:
    subscribe:
      message: {payload: {type: object}}
`)
	write(t, dir, ".env", "PAYMENT_URL=http://payment-service:4005\nDB=postgres://orders-db:5432/x\n")
	write(t, dir, "docker-compose.yml", `services:
  gateway:
    environment:
      ORDERS: http://order-service:4004
  order-service:
    environment:
      - USERS=http://user-service:4001
`)
	write(t, dir, "node_modules/x/openapi.yaml", "openapi: 3.0.0\npaths: {/ignored: {get: {}}}\n")

	r, err := scanDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	names := []string{}
	for _, op := range r.Operations {
		names = append(names, op.Protocol+" "+op.Name)
	}
	want := "HTTP GET /orders/{id},HTTP POST /orders,GRPC shop.payments.Payment/Charge,MESSAGING publish order.created"
	got := strings.Join(sortedStrings(names), ",")
	if got != strings.Join(sortedStrings(strings.Split(want, ",")), ",") {
		t.Fatalf("operations: %s", got)
	}
	for _, op := range r.Operations {
		if op.Name == "POST /orders" && !strings.Contains(op.RequestSchema, `"userId"`) {
			t.Fatalf("OpenAPI $ref not resolved: %s", op.RequestSchema)
		}
		if op.Protocol == "GRPC" && !strings.Contains(op.RequestSchema, `"amount":{"type":"number"}`) {
			t.Fatalf("proto schema: %s", op.RequestSchema)
		}
	}
	if strings.Join(r.References, ",") != "payment-service" {
		t.Fatalf("references: %v", r.References)
	}
	if strings.Join(r.ComposeReferences["gateway"], ",") != "order-service" || strings.Join(r.ComposeReferences["order-service"], ",") != "user-service" {
		t.Fatalf("compose: %v", r.ComposeReferences)
	}
}

func sortedStrings(s []string) []string {
	out := append([]string(nil), s...)
	sort.Strings(out)
	return out
}

// A compose-only repository declares its services' dependencies without registering itself.
func TestScanOfAComposeOnlyRepoDoesNotRegisterIt(t *testing.T) {
	dir := t.TempDir()
	write(t, dir, "docker-compose.yml", "services:\n  gateway:\n    environment:\n      ORDERS: http://orders:4004\n")
	var sent []scanRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/services"):
			json.NewEncoder(w).Encode([]Service{{Name: "gateway"}, {Name: "orders"}})
		case strings.HasSuffix(r.URL.Path, "/catalog/scan"):
			var req scanRequest
			json.NewDecoder(r.Body).Decode(&req)
			sent = append(sent, req)
			json.NewEncoder(w).Encode(scanResponse{DependenciesAdded: req.Dependencies})
		}
	}))
	defer srv.Close()
	t.Setenv("SDNA_CONFIG", filepath.Join(t.TempDir(), "config.json"))

	c := newClient(&Config{URL: srv.URL, Token: "t", OrgID: "o1"})
	if err := cmdScan(c, []string{"--dir", dir}); err != nil {
		t.Fatal(err)
	}
	if len(sent) != 1 || sent[0].Service != "gateway" || strings.Join(sent[0].Dependencies, ",") != "orders" {
		t.Fatalf("sent %+v", sent)
	}
}

func TestFlowFilesBecomeSuites(t *testing.T) {
	dir := t.TempDir()
	write(t, dir, "checkout.yaml", `environment: dev
cases:
  - name: place an order
    request:
      service: api-gateway
      method: POST
      path: /api/orders
      body: {userId: u-1, items: [{productId: p-2}]}
    expect:
      - {entry: true, status: 201}
      - {service: payment-service, operation: Charge, exists: true}
`)
	files, err := flowFiles([]string{dir})
	if err != nil || len(files) != 1 {
		t.Fatalf("files %v %v", files, err)
	}
	start, err := loadFlow(files[0], "staging")
	if err != nil {
		t.Fatal(err)
	}
	out, _ := json.Marshal(start)
	for _, want := range []string{
		`"name":"checkout"`, `"environment":"staging"`, `"serviceName":"api-gateway"`, `"protocol":"HTTP"`,
		`"body":"{\"items\":[{\"productId\":\"p-2\"}],\"userId\":\"u-1\"}"`,
		`"target":{"entry":true}`, `"target":{"operation":"Charge","service":"payment-service"}`, `"exists":true`,
	} {
		if !strings.Contains(string(out), want) {
			t.Fatalf("missing %s in %s", want, out)
		}
	}
	if got := strings.Join(reorder([]string{"flows/", "--env", "dev"}), " "); got != "--env dev flows/" {
		t.Fatalf("reorder: %s", got)
	}
}
