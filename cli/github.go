package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"html"
	"net"
	"net/http"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"time"
)

// cmdGitHub: `sdna github create-app` registers the ServiceDNA GitHub App through GitHub's
// app-manifest flow and writes its credentials as GITHUB_APP_* settings for the backend.
func cmdGitHub(args []string) error {
	if len(args) == 0 || args[0] != "create-app" {
		return errors.New("usage: sdna github create-app (--url https://servicedna.example.com | --poll --app-url URL) [--app-url URL] [--org GITHUB_ORG] [--name NAME] [--public] [--out FILE]")
	}
	fs := flag.NewFlagSet("github create-app", flag.ContinueOnError)
	apiURL := fs.String("url", "", "ServiceDNA's public API URL; GitHub sends webhooks to <url>/api/v1/github/webhook")
	appURL := fs.String("app-url", "", "ServiceDNA's web app URL, where GitHub returns after an install (default: --url)")
	org := fs.String("org", "", "GitHub organization to own the app (default: your account)")
	name := fs.String("name", "ServiceDNA", "app name (unique on GitHub)")
	public := fs.Bool("public", false, "let any GitHub account install it (for a shared ServiceDNA)")
	poll := fs.Bool("poll", false, "no webhooks: ServiceDNA asks GitHub for changes every minute (for a ServiceDNA GitHub can't reach)")
	out := fs.String("out", ".env.github-app", "file to write the app's settings to")
	if err := fs.Parse(reorder(args[1:])); err != nil {
		return err
	}
	if *appURL == "" {
		*appURL = *apiURL
	}
	if *apiURL == "" && !*poll {
		return errors.New("--url is required: the address GitHub can reach ServiceDNA's API on (or --poll if it can't reach it)")
	}
	if *appURL == "" {
		return errors.New("--app-url is required: ServiceDNA's web app URL, where your browser returns after an install")
	}
	return createApp(*apiURL, *appURL, *org, *name, *public, *poll, *out, func(base string) {
		fmt.Printf("→ Open %s in your browser to create the app on GitHub (opening it for you)…\n", base)
		openBrowser(base)
	})
}

// createApp runs the manifest flow; ready is told the local page to open.
func createApp(apiURL, appURL, org, name string, public, poll bool, out string, ready func(base string)) error {
	webURL := strings.TrimRight(orDefault(os.Getenv("GITHUB_WEB_URL"), "https://github.com"), "/")
	ghAPI := strings.TrimRight(orDefault(os.Getenv("GITHUB_API_URL"), "https://api.github.com"), "/")

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return err
	}
	base := "http://" + listener.Addr().String()
	stateBytes := make([]byte, 16)
	if _, err := rand.Read(stateBytes); err != nil {
		return err
	}
	state := hex.EncodeToString(stateBytes)
	manifest := appManifest(name, strings.TrimRight(apiURL, "/"), strings.TrimRight(appURL, "/"), base+"/callback", public, poll)
	target := webURL + "/settings/apps/new"
	if org != "" {
		target = webURL + "/organizations/" + org + "/settings/apps/new"
	}

	type result struct {
		app map[string]any
		err error
	}
	done := make(chan result, 1)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `<!doctype html><title>Create the ServiceDNA GitHub App</title>
<body style="font-family:sans-serif"><p>Sending you to GitHub to create the app…</p>
<form id="f" method="post" action="%s?state=%s"><input type="hidden" name="manifest" value="%s"><button>Continue to GitHub</button></form>
<script>document.getElementById('f').submit()</script>`, html.EscapeString(target), state, html.EscapeString(manifest))
	})
	mux.HandleFunc("GET /callback", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("state") != state {
			http.Error(w, "state mismatch — start again with sdna github create-app", http.StatusBadRequest)
			return
		}
		app, err := convertManifest(ghAPI, r.URL.Query().Get("code"))
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			done <- result{err: err}
			return
		}
		fmt.Fprint(w, "<!doctype html><body style=\"font-family:sans-serif\"><p>✓ GitHub app created. Back to your terminal.</p>")
		done <- result{app: app}
	})
	server := &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go server.Serve(listener)
	defer server.Shutdown(context.Background())

	ready(base)
	var res result
	select {
	case res = <-done:
	case <-time.After(15 * time.Minute):
		return errors.New("timed out waiting for GitHub")
	}
	if res.err != nil {
		return res.err
	}
	if err := os.WriteFile(out, []byte(appEnv(res.app, poll)), 0o600); err != nil {
		return err
	}
	fmt.Printf("✓ created GitHub app %q (%s/apps/%v)\n", res.app["name"], webURL, res.app["slug"])
	fmt.Printf("✓ wrote its settings to %s (secrets — keep it out of version control)\n", out)
	fmt.Println("\nGive them to the ServiceDNA backend, e.g. with docker compose:\n\n  set -a; . ./" + out + "; set +a\n  docker compose up -d backend\n\nThen connect it: Settings → Integrations → GitHub App → Install.")
	return nil
}

// appManifest describes the app GitHub creates: read code and metadata, write checks; events for
// pushes, pull requests and repository changes; GitHub sign-in during install (to prove who
// installed it), also when the installation is changed later. Polling turns webhooks off.
func appManifest(name, apiURL, appURL, redirect string, public, poll bool) string {
	m := map[string]any{
		"name":                     name,
		"url":                      appURL,
		"redirect_url":             redirect,
		"callback_urls":            []string{appURL + "/github/setup"},
		"setup_url":                appURL + "/github/setup",
		"setup_on_update":          true,
		"request_oauth_on_install": true,
		"public":                   public,
		"default_permissions": map[string]string{
			"contents":      "read",
			"metadata":      "read",
			"checks":        "write",
			"pull_requests": "read",
		},
	}
	if !poll { // polling: no webhook at all (GitHub refuses unreachable hook URLs, even inactive)
		m["hook_attributes"] = map[string]any{"url": apiURL + "/api/v1/github/webhook", "active": true}
		m["default_events"] = []string{"push", "pull_request", "installation_repositories"}
	}
	b, _ := json.Marshal(m)
	return string(b)
}

func convertManifest(apiURL, code string) (map[string]any, error) {
	if code == "" {
		return nil, errors.New("GitHub didn't return a code")
	}
	req, _ := http.NewRequest(http.MethodPost, apiURL+"/app-manifests/"+code+"/conversions", nil)
	req.Header.Set("Accept", "application/vnd.github+json")
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	var app map[string]any
	if err := json.NewDecoder(res.Body).Decode(&app); err != nil {
		return nil, err
	}
	if res.StatusCode >= 300 {
		return nil, fmt.Errorf("GitHub refused the app: HTTP %d %v", res.StatusCode, app["message"])
	}
	return app, nil
}

// appEnv writes the settings the backend reads; the PEM goes on one line with \n escapes. The
// webhook secret also signs install links, so one is made up when GitHub has none (no webhooks).
func appEnv(app map[string]any, poll bool) string {
	pem := strings.ReplaceAll(strings.TrimSpace(fmt.Sprint(app["pem"])), "\n", `\n`)
	secret, _ := app["webhook_secret"].(string)
	if secret == "" {
		b := make([]byte, 32)
		_, _ = rand.Read(b)
		secret = hex.EncodeToString(b)
	}
	return fmt.Sprintf("GITHUB_APP_ID=%v\nGITHUB_APP_SLUG=%v\nGITHUB_APP_CLIENT_ID=%v\nGITHUB_APP_CLIENT_SECRET=%v\nGITHUB_APP_WEBHOOK_SECRET=%v\nGITHUB_APP_PRIVATE_KEY=\"%s\"\nGITHUB_APP_POLL=%t\n",
		jsonNumber(app["id"]), app["slug"], app["client_id"], app["client_secret"], secret, pem, poll)
}

func jsonNumber(v any) string {
	if f, ok := v.(float64); ok {
		return fmt.Sprintf("%.0f", f)
	}
	return fmt.Sprint(v)
}

func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}
