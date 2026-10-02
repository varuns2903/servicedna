package main

import (
	"encoding/json"
	"html"
	"io"
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

func TestServicednaYamlIsSentWithTheScan(t *testing.T) {
	dir := t.TempDir()
	write(t, dir, "servicedna.yaml", `service: orders
owner: team-checkout
tier: critical
slo: 99.95
health: http://orders:4004/health
dependencies: [payments, fraud]
alerts:
  - condition: status_down
    open_incident: critical
  - condition: LATENCY_ABOVE
    threshold: 800
    window_minutes: 5
    webhook: https://hooks.example.com/x
    integration: slack
`)
	var sent []scanRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/services"):
			json.NewEncoder(w).Encode([]Service{{Name: "orders"}, {Name: "payments"}})
		case strings.HasSuffix(r.URL.Path, "/catalog/scan"):
			var req scanRequest
			json.NewDecoder(r.Body).Decode(&req)
			sent = append(sent, req)
			two := 2
			json.NewEncoder(w).Encode(scanResponse{DependenciesAdded: []string{"payments"}, DependenciesUnknown: []string{"fraud"}, Updated: []string{"owner"}, AlertRules: &two})
		}
	}))
	defer srv.Close()
	t.Setenv("SDNA_CONFIG", filepath.Join(t.TempDir(), "config.json"))

	if err := cmdScan(newClient(&Config{URL: srv.URL, Token: "t", OrgID: "o1"}), []string{"--dir", dir}); err != nil {
		t.Fatal(err)
	}
	if len(sent) != 1 {
		t.Fatalf("sent %+v", sent)
	}
	req := sent[0]
	if req.Service != "orders" || strings.Join(req.Dependencies, ",") != "payments,fraud" {
		t.Fatalf("request %+v", req)
	}
	if req.Metadata == nil || req.Metadata.Owner != "team-checkout" || req.Metadata.Tier != "critical" || *req.Metadata.SLO != 99.95 || req.Metadata.HealthURL != "http://orders:4004/health" {
		t.Fatalf("metadata %+v", req.Metadata)
	}
	if len(req.Alerts) != 2 || req.Alerts[0].Condition != "STATUS_DOWN" || req.Alerts[0].IncidentSeverity != "CRITICAL" ||
		req.Alerts[1].IntegrationType != "SLACK" || *req.Alerts[1].WindowMinutes != 5 {
		t.Fatalf("alerts %+v", req.Alerts)
	}
}

func TestServicednaYamlMistakesAreReported(t *testing.T) {
	for content, want := range map[string]string{
		"service: x\nteir: high\n":                          "field teir not found",
		"service: x\ntier: urgent\n":                        "tier \"urgent\"",
		"service: x\nhealth: /health\n":                     "full URL",
		"service: x\nalerts:\n  - condition: STATUS_DOWN\n": "needs open_incident, webhook",
	} {
		dir := t.TempDir()
		write(t, dir, "servicedna.yaml", content)
		if _, _, err := loadManifest(dir); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%q: got %v, want %q", content, err, want)
		}
	}
	dir := t.TempDir()
	if m, _, err := loadManifest(dir); m != nil || err != nil {
		t.Fatalf("no file: %v %v", m, err)
	}
	write(t, dir, "servicedna.yaml", manifestTemplate("orders"))
	m, _, err := loadManifest(dir)
	if err != nil || m.Service != "orders" || len(m.alerts()) != 1 || m.metadata().Tier != "medium" {
		t.Fatalf("template doesn't load: %+v %v", m, err)
	}
}

func TestCIRunsFromEnvironmentVariables(t *testing.T) {
	var auth []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		auth = append(auth, r.Header.Get("Authorization")+" "+r.URL.Path)
		switch {
		case strings.HasSuffix(r.URL.Path, "/organizations"):
			json.NewEncoder(w).Encode([]Organization{{ID: "11111111-2222-3333-4444-555555555555", Name: "ShopLite"}})
		case strings.HasSuffix(r.URL.Path, "/services"):
			json.NewEncoder(w).Encode([]Service{{Name: "orders"}})
		case strings.HasSuffix(r.URL.Path, "/catalog/scan"):
			json.NewEncoder(w).Encode(scanResponse{})
		}
	}))
	defer srv.Close()
	configFile := filepath.Join(t.TempDir(), "config.json")
	t.Setenv("SDNA_CONFIG", configFile)
	t.Setenv("SDNA_URL", srv.URL+"/")
	t.Setenv("SDNA_TOKEN", "sdna_pat_abc")
	t.Setenv("SDNA_ORG", "shoplite")

	cfg, err := loadConfig()
	if err != nil {
		t.Fatal(err)
	}
	dir := t.TempDir()
	write(t, dir, "servicedna.yaml", "service: orders\nowner: team-checkout\n")
	if err := cmdScan(newClient(cfg), []string{"--dir", dir}); err != nil {
		t.Fatal(err)
	}
	want := "Bearer sdna_pat_abc /api/v1/organizations/11111111-2222-3333-4444-555555555555/catalog/scan"
	if auth[len(auth)-1] != want {
		t.Fatalf("requests %v", auth)
	}
	if err := cfg.save(); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(configFile); !os.IsNotExist(err) {
		t.Fatal("a token from the environment must not be written to the config file")
	}

	var c Config
	c.applyEnv(func(k string) string { return map[string]string{"SDNA_ORG": "11111111-2222-3333-4444-555555555555"}[k] })
	if c.OrgID == "" || c.fromEnv {
		t.Fatalf("org id: %+v", c)
	}
}

func TestMarkdownReport(t *testing.T) {
	var s suite
	json.Unmarshal([]byte(`{"name":"Checkout","status":"FAILED","passed":1,"failed":1,"runs":[
	  {"caseName":"pays","traceId":"aa","passed":true,"assertionResults":[{"description":"entry status eq 201","passed":true}]},
	  {"caseName":"declines | big","traceId":"bb","passed":false,"assertionResults":[{"description":"entry status eq 402","passed":false,"message":"got 201"}]}]}`), &s)
	out := markdownReport([]suite{s}, "https://sdna.example.com/")
	for _, want := range []string{
		"### ❌ ServiceDNA test flows — 1 passed, 1 failed",
		"| ✅ | [pays](https://sdna.example.com/traces?trace=aa) |  |",
		"| ❌ | [declines \\| big](https://sdna.example.com/traces?trace=bb) | entry status eq 402 — got 201 |",
	} {
		if !strings.Contains(out, want) {
			t.Fatalf("missing %q in:\n%s", want, out)
		}
	}
	if strings.Contains(markdownReport([]suite{s}, ""), "](") {
		t.Fatal("no links without an app URL")
	}
}

func TestCreateGitHubAppThroughTheManifestFlow(t *testing.T) {
	var conversions []string
	github := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conversions = append(conversions, r.Method+" "+r.URL.Path)
		json.NewEncoder(w).Encode(map[string]any{
			"id": 123456, "slug": "servicedna-acme", "name": "ServiceDNA", "client_id": "Iv1.abc", "client_secret": "cs",
			"webhook_secret": "whsec", "pem": "-----BEGIN RSA PRIVATE KEY-----\nAAA\nBBB\n-----END RSA PRIVATE KEY-----\n",
		})
	}))
	defer github.Close()
	t.Setenv("GITHUB_API_URL", github.URL)
	t.Setenv("GITHUB_WEB_URL", "https://github.example")
	out := filepath.Join(t.TempDir(), ".env.github-app")

	bases := make(chan string, 1)
	errs := make(chan error, 1)
	go func() {
		errs <- createApp("https://sdna.example.com", "https://app.sdna.example.com", "acme", "ServiceDNA", false, false, out, func(base string) { bases <- base })
	}()
	base := <-bases

	// The local page posts the manifest to GitHub's "new app" form for the organization.
	res, err := http.Get(base)
	if err != nil {
		t.Fatal(err)
	}
	page, _ := io.ReadAll(res.Body)
	body := html.UnescapeString(string(page))
	if !strings.Contains(body, `action="https://github.example/organizations/acme/settings/apps/new?state=`) {
		t.Fatalf("page: %s", body)
	}
	for _, want := range []string{`"url":"https://sdna.example.com/api/v1/github/webhook"`, `"setup_url":"https://app.sdna.example.com/github/setup"`,
		`"checks":"write"`, `"request_oauth_on_install":true`, `"setup_on_update":true`, `"active":true`, `"redirect_url":"` + base + `/callback"`} {
		if !strings.Contains(body, want) {
			t.Fatalf("manifest lacks %s: %s", want, body)
		}
	}
	state := body[strings.Index(body, "state=")+6:]
	state = state[:strings.Index(state, `"`)]

	// A wrong state is refused; GitHub's redirect with the right one completes it.
	if r, _ := http.Get(base + "/callback?code=c0de&state=nope"); r.StatusCode != http.StatusBadRequest {
		t.Fatalf("bad state: %d", r.StatusCode)
	}
	if r, _ := http.Get(base + "/callback?code=c0de&state=" + state); r.StatusCode != http.StatusOK {
		t.Fatalf("callback: %d", r.StatusCode)
	}
	if err := <-errs; err != nil {
		t.Fatal(err)
	}
	if len(conversions) != 1 || conversions[0] != "POST /app-manifests/c0de/conversions" {
		t.Fatalf("conversions: %v", conversions)
	}
	info, _ := os.Stat(out)
	env, _ := os.ReadFile(out)
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("mode %v", info.Mode())
	}
	for _, want := range []string{"GITHUB_APP_ID=123456\n", "GITHUB_APP_SLUG=servicedna-acme\n", "GITHUB_APP_WEBHOOK_SECRET=whsec\n",
		`GITHUB_APP_PRIVATE_KEY="-----BEGIN RSA PRIVATE KEY-----\nAAA\nBBB\n-----END RSA PRIVATE KEY-----"`} {
		if !strings.Contains(string(env), want) {
			t.Fatalf("env lacks %q:\n%s", want, env)
		}
	}
}

func TestAPollingAppHasNoWebhooks(t *testing.T) {
	var m map[string]any
	json.Unmarshal([]byte(appManifest("ServiceDNA", "", "http://localhost:5173", "http://127.0.0.1:1/callback", false, true)), &m)
	hook := m["hook_attributes"].(map[string]any)
	if hook["active"] != false || m["default_events"] != nil || m["public"] != false {
		t.Fatalf("manifest: %v", m)
	}
	env := appEnv(map[string]any{"id": 1.0, "slug": "s", "client_id": "c", "client_secret": "cs", "pem": "k"}, true)
	if !strings.Contains(env, "GITHUB_APP_POLL=true\n") || strings.Contains(env, "GITHUB_APP_WEBHOOK_SECRET=\n") || strings.Contains(env, "<nil>") {
		t.Fatalf("env: %s", env)
	}
}
