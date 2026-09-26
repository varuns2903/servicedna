package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
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
