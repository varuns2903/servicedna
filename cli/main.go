// sdna is the ServiceDNA command-line interface.
package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"text/tabwriter"
	"time"

	"golang.org/x/term"
)

const usage = `sdna — ServiceDNA from your terminal

Usage:
  sdna login [--url URL] [--email EMAIL]   sign in (password is prompted, or SDNA_PASSWORD)
  sdna login --token sdna_pat_…           sign in with an API token (SSO accounts)
  sdna orgs                                list your organizations
  sdna use <org name or id>                choose the organization other commands use
  sdna status                              services and open incidents
  sdna keys list                           ingestion keys
  sdna keys create <name>                  create an ingestion key (shown once)
  sdna init [--env ENV] [--no-install]     connect the project in this directory
  sdna scan [--service NAME] [--env ENV]   send this repo's servicedna.yaml, API specs and configured dependencies
            [--dry-run]                    (OpenAPI, .proto, AsyncAPI, compose/.env/k8s URLs)
  sdna test run <flow.yaml|dir>...         run test flows (or --collection NAME); exits 1 on failure
            [--env ENV] [--timeout 5m]

Settings are stored in ~/.config/servicedna/config.json (override with SDNA_CONFIG). In CI, set
SDNA_URL, SDNA_TOKEN (an API token from Settings → Account) and SDNA_ORG instead of signing in.
`

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "✗", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 || args[0] == "help" || args[0] == "-h" || args[0] == "--help" {
		fmt.Print(usage)
		return nil
	}
	cfg, err := loadConfig()
	if err != nil {
		return err
	}
	client := newClient(cfg)
	switch args[0] {
	case "login":
		return cmdLogin(client, args[1:])
	case "orgs":
		return cmdOrgs(client)
	case "use":
		return cmdUse(client, args[1:])
	case "status":
		return cmdStatus(client)
	case "keys":
		return cmdKeys(client, args[1:])
	case "init":
		return cmdInit(client, args[1:])
	case "scan":
		return cmdScan(client, args[1:])
	case "test":
		return cmdTest(client, args[1:])
	}
	return fmt.Errorf("unknown command %q (see `sdna help`)", args[0])
}

func cmdLogin(c *Client, args []string) error {
	fs := flag.NewFlagSet("login", flag.ContinueOnError)
	url := fs.String("url", orDefault(c.cfg.URL, "http://localhost:8080"), "ServiceDNA base URL")
	email := fs.String("email", c.cfg.Email, "account email")
	token := fs.String("token", "", "sign in with an API token (sdna_pat_…) instead of a password — for SSO accounts")
	if err := fs.Parse(args); err != nil {
		return err
	}
	c.cfg.URL = strings.TrimRight(*url, "/")
	if *token != "" {
		c.cfg.Token, c.cfg.RefreshToken = *token, ""
		var me struct {
			Email string `json:"email"`
		}
		if err := c.do(http.MethodGet, "/users/me", nil, &me); err != nil {
			return fmt.Errorf("login failed: %w", err)
		}
		c.cfg.Email = me.Email
	} else if *email == "" {
		*email = prompt("Email: ")
	}
	if *token == "" {
		password := os.Getenv("SDNA_PASSWORD")
		if password == "" {
			fmt.Print("Password: ")
			raw, err := term.ReadPassword(int(os.Stdin.Fd()))
			fmt.Println()
			if err != nil {
				return err
			}
			password = string(raw)
		}
		if err := c.login(*email, password); err != nil {
			return fmt.Errorf("login failed: %w", err)
		}
	}

	var orgs []Organization
	if err := c.do(http.MethodGet, "/organizations", nil, &orgs); err != nil {
		return err
	}
	if c.cfg.OrgID == "" && len(orgs) > 0 {
		c.cfg.OrgID, c.cfg.OrgName = orgs[0].ID, orgs[0].Name
	}
	if err := c.cfg.save(); err != nil {
		return err
	}
	fmt.Printf("✓ signed in to %s as %s", c.cfg.URL, c.cfg.Email)
	if c.cfg.OrgName != "" {
		fmt.Printf(" (organization: %s)", c.cfg.OrgName)
	}
	fmt.Println()
	return nil
}

func cmdOrgs(c *Client) error {
	if err := requireLogin(c); err != nil {
		return err
	}
	var orgs []Organization
	if err := c.do(http.MethodGet, "/organizations", nil, &orgs); err != nil {
		return err
	}
	for _, o := range orgs {
		marker := "  "
		if o.ID == c.cfg.OrgID {
			marker = "* "
		}
		fmt.Printf("%s%s  %s\n", marker, o.Name, o.ID)
	}
	return nil
}

func cmdUse(c *Client, args []string) error {
	if err := requireLogin(c); err != nil {
		return err
	}
	if len(args) != 1 {
		return errors.New("usage: sdna use <org name or id>")
	}
	var orgs []Organization
	if err := c.do(http.MethodGet, "/organizations", nil, &orgs); err != nil {
		return err
	}
	for _, o := range orgs {
		if o.ID == args[0] || strings.EqualFold(o.Name, args[0]) {
			c.cfg.OrgID, c.cfg.OrgName = o.ID, o.Name
			if err := c.cfg.save(); err != nil {
				return err
			}
			fmt.Printf("✓ using %s\n", o.Name)
			return nil
		}
	}
	return fmt.Errorf("no organization named %q (see `sdna orgs`)", args[0])
}

func cmdStatus(c *Client) error {
	if err := requireOrg(c); err != nil {
		return err
	}
	var services []Service
	if err := c.do(http.MethodGet, "/organizations/"+c.cfg.OrgID+"/services", nil, &services); err != nil {
		return err
	}
	var incidents []Incident
	if err := c.do(http.MethodGet, "/organizations/"+c.cfg.OrgID+"/incidents", nil, &incidents); err != nil {
		return err
	}

	fmt.Printf("%s — %d services\n\n", c.cfg.OrgName, len(services))
	w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
	fmt.Fprintln(w, "SERVICE\tSTATUS\tENV\tLANGUAGE\tLAST TELEMETRY")
	for _, s := range services {
		lang := deref(s.Language)
		if v := deref(s.Version); v != "" && lang != "" {
			lang += " " + v
		}
		fmt.Fprintf(w, "%s\t%s %s\t%s\t%s\t%s\n", s.Name, statusIcon(s.Status), s.Status, orDefault(deref(s.Environment), "-"), orDefault(lang, "-"), ago(s.LastTelemetryAt))
	}
	w.Flush()

	open := 0
	for _, i := range incidents {
		if i.Status != "RESOLVED" {
			if open == 0 {
				fmt.Println("\nOpen incidents:")
			}
			open++
			fmt.Printf("  [%s] %s — %s, %s\n", i.Severity, i.Title, i.Status, ago(&i.CreatedAt))
		}
	}
	if open == 0 {
		fmt.Println("\nNo open incidents.")
	}
	return nil
}

func cmdKeys(c *Client, args []string) error {
	if err := requireOrg(c); err != nil {
		return err
	}
	path := "/organizations/" + c.cfg.OrgID + "/ingestion-keys"
	switch {
	case len(args) == 0 || args[0] == "list":
		var keys []IngestionKey
		if err := c.do(http.MethodGet, path, nil, &keys); err != nil {
			return err
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "NAME\tPREFIX\tLAST USED\tSTATE")
		for _, k := range keys {
			state := "active"
			if k.RevokedAt != nil {
				state = "revoked"
			}
			fmt.Fprintf(w, "%s\t%s…\t%s\t%s\n", k.Name, k.KeyPrefix, ago(k.LastUsedAt), state)
		}
		return w.Flush()
	case args[0] == "create" && len(args) == 2:
		key, err := createKey(c, args[1])
		if err != nil {
			return err
		}
		fmt.Printf("✓ created %q — copy it now, it won't be shown again:\n\n  %s\n", args[1], key)
		return nil
	}
	return errors.New("usage: sdna keys [list | create <name>]")
}

func createKey(c *Client, name string) (string, error) {
	var created IngestionKey
	if err := c.do(http.MethodPost, "/organizations/"+c.cfg.OrgID+"/ingestion-keys", map[string]string{"name": name}, &created); err != nil {
		return "", err
	}
	return deref(created.Key), nil
}

func cmdInit(c *Client, args []string) error {
	fs := flag.NewFlagSet("init", flag.ContinueOnError)
	env := fs.String("env", "dev", "environment the service reports")
	noInstall := fs.Bool("no-install", false, "don't add the SDK dependency")
	dir := fs.String("dir", ".", "project directory")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := requireOrg(c); err != nil {
		return err
	}
	abs, err := filepath.Abs(*dir)
	if err != nil {
		return err
	}
	stack, ok := detectStack(abs)
	if !ok {
		return fmt.Errorf("no package.json, pyproject.toml/requirements.txt, go.mod or pom.xml/build.gradle in %s", abs)
	}
	fmt.Printf("→ %s project, service %q, environment %q\n", stack.Name, stack.Service, *env)

	key, err := createKey(c, stack.Service+" "+*env)
	if err != nil {
		return err
	}
	fmt.Printf("✓ created ingestion key for %s (%s)\n", stack.Service, *env)

	envFile := filepath.Join(abs, ".env.servicedna")
	contents := fmt.Sprintf("SERVICEDNA_URL=%s\nSERVICEDNA_KEY=%s\nSERVICEDNA_ENV=%s\nOTEL_SERVICE_NAME=%s\n", c.cfg.URL, key, *env, stack.Service)
	if err := os.WriteFile(envFile, []byte(contents), 0o600); err != nil {
		return err
	}
	fmt.Println("✓ wrote .env.servicedna (contains a secret — keep it out of version control)")
	ensureGitignored(abs, ".env.servicedna")
	if m, _, _ := loadManifest(abs); m == nil {
		if err := os.WriteFile(filepath.Join(abs, "servicedna.yaml"), []byte(manifestTemplate(stack.Service)), 0o644); err != nil {
			return err
		}
		fmt.Println("✓ wrote servicedna.yaml — owner, tier, SLO and alerts, applied by `sdna scan` (commit it)")
	}

	if len(stack.Install) > 0 && !*noInstall {
		fmt.Printf("→ %s\n", strings.Join(stack.Install, " "))
		cmd := exec.Command(stack.Install[0], stack.Install[1:]...)
		cmd.Dir, cmd.Stdout, cmd.Stderr = abs, os.Stdout, os.Stderr
		if err := cmd.Run(); err != nil {
			return fmt.Errorf("installing the SDK failed: %w", err)
		}
		fmt.Println("✓ added the ServiceDNA SDK")
	}
	for _, note := range stack.Notes {
		fmt.Printf("\n• %s\n", note)
	}
	fmt.Printf("\nStart the service with the settings loaded, e.g.:\n\n  set -a; . ./.env.servicedna; set +a\n  %s\n\nIt will appear in ServiceDNA within a few seconds — `sdna status` to check.\n", stack.Run)
	return nil
}

func ensureGitignored(dir, name string) {
	path := filepath.Join(dir, ".gitignore")
	data, _ := os.ReadFile(path)
	for _, line := range strings.Split(string(data), "\n") {
		if strings.TrimSpace(line) == name {
			return
		}
	}
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return
	}
	defer f.Close()
	if len(data) > 0 && !strings.HasSuffix(string(data), "\n") {
		fmt.Fprintln(f)
	}
	fmt.Fprintln(f, name)
	fmt.Println("✓ added .env.servicedna to .gitignore")
}

func requireLogin(c *Client) error {
	if c.cfg.Token == "" {
		return errors.New("not signed in; run `sdna login` (or set SDNA_TOKEN)")
	}
	return nil
}

func requireOrg(c *Client) error {
	if err := requireLogin(c); err != nil {
		return err
	}
	if c.cfg.OrgID == "" && c.cfg.OrgName != "" {
		// SDNA_ORG gave a name: find its id.
		var orgs []Organization
		if err := c.do(http.MethodGet, "/organizations", nil, &orgs); err != nil {
			return err
		}
		for _, o := range orgs {
			if strings.EqualFold(o.Name, c.cfg.OrgName) {
				c.cfg.OrgID = o.ID
				return nil
			}
		}
		return fmt.Errorf("no organization named %q", c.cfg.OrgName)
	}
	if c.cfg.OrgID == "" {
		return errors.New("no organization selected; run `sdna use <org>` (or set SDNA_ORG)")
	}
	return nil
}

func prompt(label string) string {
	fmt.Print(label)
	line, _ := bufio.NewReader(os.Stdin).ReadString('\n')
	return strings.TrimSpace(line)
}

func statusIcon(status string) string {
	switch status {
	case "HEALTHY":
		return "●"
	case "DEGRADED":
		return "◐"
	case "DOWN":
		return "○"
	}
	return "·"
}

func ago(ts *string) string {
	if ts == nil || *ts == "" {
		return "never"
	}
	t, err := time.Parse(time.RFC3339Nano, *ts)
	if err != nil {
		return *ts
	}
	d := time.Since(t).Round(time.Second)
	switch {
	case d < time.Minute:
		return fmt.Sprintf("%ds ago", int(d.Seconds()))
	case d < time.Hour:
		return fmt.Sprintf("%dm ago", int(d.Minutes()))
	case d < 48*time.Hour:
		return fmt.Sprintf("%dh ago", int(d.Hours()))
	}
	return fmt.Sprintf("%dd ago", int(d.Hours()/24))
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

type scanRequest struct {
	Service      string        `json:"service"`
	Environment  string        `json:"environment,omitempty"`
	Operations   []Operation   `json:"operations"`
	Dependencies []string      `json:"dependencies"`
	Metadata     *scanMetadata `json:"metadata,omitempty"`
	Alerts       []alertRule   `json:"alerts,omitempty"`
}

type scanResponse struct {
	ServiceID           string   `json:"serviceId"`
	Operations          int      `json:"operations"`
	DependenciesAdded   []string `json:"dependenciesAdded"`
	DependenciesUnknown []string `json:"dependenciesUnknown"`
	Updated             []string `json:"updated"`
	AlertRules          *int     `json:"alertRules"`
}

func cmdScan(c *Client, args []string) error {
	fs := flag.NewFlagSet("scan", flag.ContinueOnError)
	dir := fs.String("dir", ".", "repository to scan")
	service := fs.String("service", "", "service the repository is (default: from the project's metadata)")
	env := fs.String("env", "", "environment (default: whichever the service is registered in)")
	dryRun := fs.Bool("dry-run", false, "show what would be sent")
	if err := fs.Parse(args); err != nil {
		return err
	}
	abs, err := filepath.Abs(*dir)
	if err != nil {
		return err
	}
	result, err := scanDir(abs)
	if err != nil {
		return err
	}
	manifest, manifestFile, err := loadManifest(abs)
	if err != nil {
		return err
	}
	if *service == "" && manifest != nil && manifest.Service != "" {
		*service = manifest.Service
	}
	if *service == "" {
		if stack, ok := detectStack(abs); ok {
			*service = stack.Service
		} else {
			*service = filepath.Base(abs)
		}
	}

	// Only names of registered services become dependencies; other hosts (databases, SaaS APIs)
	// are reported, not declared.
	known := map[string]bool{}
	if !*dryRun || c.cfg.Token != "" {
		if err := requireOrg(c); err != nil {
			return err
		}
		var services []Service
		if err := c.do(http.MethodGet, "/organizations/"+c.cfg.OrgID+"/services", nil, &services); err != nil {
			return err
		}
		for _, s := range services {
			known[strings.ToLower(s.Name)] = true
		}
	}
	split := func(refs []string, self string) (deps, other []string) {
		for _, r := range refs {
			switch {
			case r == strings.ToLower(self):
			case known[r]:
				deps = append(deps, r)
			default:
				other = append(other, r)
			}
		}
		return deps, other
	}

	deps, other := split(result.References, *service)
	main := scanRequest{Service: *service, Environment: *env, Dependencies: nonNil(deps)}
	if len(result.Operations) > 0 {
		main.Operations = result.Operations
	}
	if manifest != nil {
		// Declared dependencies are sent as written; ServiceDNA reports the ones it doesn't know.
		main.Dependencies = nonNil(unique(append(main.Dependencies, manifest.Dependencies...)))
		main.Metadata = manifest.metadata()
		main.Alerts = manifest.alerts()
	}
	requests := []scanRequest{main}
	var ignored []string
	ignored = append(ignored, other...)
	composeNames := make([]string, 0, len(result.ComposeReferences))
	for name := range result.ComposeReferences {
		composeNames = append(composeNames, name)
	}
	sort.Strings(composeNames)
	for _, name := range composeNames {
		if !known[strings.ToLower(name)] {
			continue // a compose service that isn't a registered service (a database, a helper)
		}
		d, o := split(result.ComposeReferences[name], name)
		ignored = append(ignored, o...)
		if name == *service {
			requests[0].Dependencies = append(requests[0].Dependencies, d...)
		} else if len(d) > 0 {
			requests = append(requests, scanRequest{Service: name, Environment: *env, Dependencies: d})
		}
	}

	// A repository with nothing about itself (only a compose file for other services, say) isn't a
	// service: don't register one named after it.
	if manifest == nil && len(requests[0].Operations) == 0 && len(requests[0].Dependencies) == 0 {
		requests = requests[1:]
	}

	fmt.Printf("→ scanned %s: %d operations from %d files\n", abs, len(result.Operations), len(result.Files))
	if manifest != nil {
		fmt.Printf("→ %s: catalog entry for %s\n", manifestFile, *service)
	}
	if *dryRun {
		out, _ := json.MarshalIndent(requests, "", "  ")
		fmt.Println(string(out))
		return nil
	}
	for _, req := range requests {
		var res scanResponse
		if err := c.do(http.MethodPost, "/organizations/"+c.cfg.OrgID+"/catalog/scan", req, &res); err != nil {
			return fmt.Errorf("%s: %w", req.Service, err)
		}
		line := fmt.Sprintf("✓ %s:", req.Service)
		if req.Operations != nil {
			line += fmt.Sprintf(" %d operations", res.Operations)
		}
		if len(res.DependenciesAdded) > 0 {
			line += " · depends on " + strings.Join(res.DependenciesAdded, ", ")
		}
		if len(res.Updated) > 0 {
			line += " · set " + strings.Join(res.Updated, ", ")
		}
		if res.AlertRules != nil {
			line += fmt.Sprintf(" · %d alert %s", *res.AlertRules, plural(*res.AlertRules, "rule", "rules"))
		}
		fmt.Println(line)
		if len(res.DependenciesUnknown) > 0 && req.Service == *service && manifest != nil {
			fmt.Printf("  (declared dependencies that aren't registered yet: %s)\n", strings.Join(res.DependenciesUnknown, ", "))
		}
	}
	if ignored = unique(ignored); len(ignored) > 0 {
		fmt.Printf("  (hosts that aren't registered services, not declared: %s)\n", strings.Join(ignored, ", "))
	}
	return nil
}

func nonNil(s []string) []string {
	if s == nil {
		return []string{}
	}
	return s
}

func plural(n int, one, many string) string {
	if n == 1 {
		return one
	}
	return many
}

func unique(s []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, v := range s {
		if !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	return out
}
