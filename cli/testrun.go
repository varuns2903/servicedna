package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

// A flow file: `sdna test run flows/checkout.yaml`.
//
//	name: Checkout
//	environment: dev
//	cases:
//	  - name: place an order
//	    request: {service: api-gateway, method: POST, path: /api/orders, body: {userId: u-1}}
//	    expect:
//	      - {entry: true, status: 201}
//	      - {service: payment-service, operation: Charge, exists: true}
type flowFile struct {
	Name        string     `yaml:"name"`
	Environment string     `yaml:"environment"`
	Cases       []flowCase `yaml:"cases"`
}

type flowCase struct {
	Name    string           `yaml:"name"`
	Request map[string]any   `yaml:"request"`
	Expect  []map[string]any `yaml:"expect"`
}

type suiteCase struct {
	Name       string         `json:"name"`
	Request    map[string]any `json:"request"`
	Assertions []any          `json:"assertions"`
}

type suite struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Status  string `json:"status"`
	Passed  int    `json:"passed"`
	Failed  int    `json:"failed"`
	Pending int    `json:"pending"`
	Runs    []struct {
		CaseName string `json:"caseName"`
		TraceID  string `json:"traceId"`
		Status   string `json:"status"`
		Error    string `json:"error"`
		Passed   *bool  `json:"passed"`
		Results  []struct {
			Description string `json:"description"`
			Passed      bool   `json:"passed"`
			Message     string `json:"message"`
		} `json:"assertionResults"`
	} `json:"runs"`
}

func cmdTest(c *Client, args []string) error {
	if len(args) == 0 || args[0] != "run" {
		return errors.New("usage: sdna test run <flow.yaml|dir>... [--env ENV] | sdna test run --collection NAME")
	}
	fs := flag.NewFlagSet("test run", flag.ContinueOnError)
	env := fs.String("env", "", "environment to run in (overrides the files')")
	collection := fs.String("collection", "", "run a saved collection by name")
	timeout := fs.Duration("timeout", 5*time.Minute, "give up waiting after this long")
	report := fs.String("report", "", "also write the results as Markdown to this file (for CI summaries and PR comments)")
	if err := fs.Parse(reorder(args[1:])); err != nil {
		return err
	}
	if err := requireOrg(c); err != nil {
		return err
	}

	var starts []map[string]any
	if *collection != "" {
		var collections []struct {
			ID   string `json:"id"`
			Name string `json:"name"`
		}
		if err := c.do(http.MethodGet, "/organizations/"+c.cfg.OrgID+"/test-collections", nil, &collections); err != nil {
			return err
		}
		id := ""
		for _, col := range collections {
			if strings.EqualFold(col.Name, *collection) {
				id = col.ID
			}
		}
		if id == "" {
			return fmt.Errorf("no collection named %q", *collection)
		}
		starts = append(starts, map[string]any{"collectionId": id, "environment": nilIfEmpty(*env)})
	}
	files, err := flowFiles(fs.Args())
	if err != nil {
		return err
	}
	for _, path := range files {
		start, err := loadFlow(path, *env)
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		starts = append(starts, start)
	}
	if len(starts) == 0 {
		return errors.New("nothing to run: give flow files/directories or --collection")
	}

	failed := false
	var finished []suite
	defer func() {
		if *report != "" {
			if err := os.WriteFile(*report, []byte(markdownReport(finished, os.Getenv("SDNA_APP_URL"))), 0o644); err != nil {
				fmt.Fprintf(os.Stderr, "writing %s: %v\n", *report, err)
			}
		}
	}()
	deadline := time.Now().Add(*timeout)
	for _, start := range starts {
		var s suite
		if err := c.do(http.MethodPost, "/organizations/"+c.cfg.OrgID+"/test-suites", start, &s); err != nil {
			return err
		}
		fmt.Printf("▶ %s (%d cases)\n", s.Name, len(s.Runs))
		for s.Status == "RUNNING" {
			if time.Now().After(deadline) {
				return fmt.Errorf("timed out waiting for %s", s.Name)
			}
			time.Sleep(2 * time.Second)
			if err := c.do(http.MethodGet, "/organizations/"+c.cfg.OrgID+"/test-suites/"+s.ID, nil, &s); err != nil {
				return err
			}
		}
		for _, run := range s.Runs {
			ok := run.Passed != nil && *run.Passed
			mark := "✓"
			if !ok {
				mark = "✗"
			}
			fmt.Printf("  %s %s\n", mark, run.CaseName)
			for _, r := range run.Results {
				if !r.Passed {
					fmt.Printf("      ✗ %s — %s\n", r.Description, r.Message)
				}
			}
		}
		fmt.Printf("  %d passed, %d failed\n", s.Passed, s.Failed)
		finished = append(finished, s)
		failed = failed || s.Status != "PASSED"
	}
	if failed {
		return errors.New("some tests failed")
	}
	return nil
}

// flowFiles expands directories to their *.yaml / *.yml files.
func flowFiles(args []string) ([]string, error) {
	var out []string
	for _, arg := range args {
		info, err := os.Stat(arg)
		if err != nil {
			return nil, err
		}
		if !info.IsDir() {
			out = append(out, arg)
			continue
		}
		for _, pattern := range []string{"*.yaml", "*.yml"} {
			matches, _ := filepath.Glob(filepath.Join(arg, pattern))
			out = append(out, matches...)
		}
	}
	return out, nil
}

// loadFlow turns a flow file into a POST /test-suites body.
func loadFlow(path, envOverride string) (map[string]any, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var f flowFile
	if err := yaml.Unmarshal(data, &f); err != nil {
		return nil, err
	}
	if len(f.Cases) == 0 {
		return nil, errors.New("no cases")
	}
	if f.Name == "" {
		f.Name = strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))
	}
	env := f.Environment
	if envOverride != "" {
		env = envOverride
	}
	var cases []suiteCase
	for i, fc := range f.Cases {
		req, err := flowRequest(fc.Request)
		if err != nil {
			return nil, fmt.Errorf("case %d (%s): %w", i+1, fc.Name, err)
		}
		var assertions []any
		for _, e := range fc.Expect {
			assertions = append(assertions, flowAssertion(e))
		}
		name := fc.Name
		if name == "" {
			name = fmt.Sprintf("case %d", i+1)
		}
		cases = append(cases, suiteCase{Name: name, Request: req, Assertions: assertions})
	}
	return map[string]any{"name": f.Name, "environment": nilIfEmpty(env), "cases": cases}, nil
}

func flowRequest(r map[string]any) (map[string]any, error) {
	out := map[string]any{}
	for k, v := range r {
		switch k {
		case "service":
			out["serviceName"] = v
		case "protocol":
			out["protocol"] = strings.ToUpper(fmt.Sprint(v))
		case "body":
			if s, ok := v.(string); ok {
				out["body"] = s
			} else {
				b, err := json.Marshal(v)
				if err != nil {
					return nil, err
				}
				out["body"] = string(b)
			}
		default:
			out[k] = v
		}
	}
	if out["protocol"] == nil {
		out["protocol"] = "HTTP"
	}
	return out, nil
}

// flowAssertion accepts {entry: true, ...} or {service: x, operation: y, ...} shorthands for target.
func flowAssertion(e map[string]any) map[string]any {
	out := map[string]any{}
	target := map[string]any{}
	for k, v := range e {
		switch k {
		case "entry":
			target["entry"] = v
		case "service", "operation":
			target[k] = v
		default:
			out[k] = v
		}
	}
	if t, ok := e["target"]; ok {
		out["target"] = t
	} else {
		out["target"] = target
	}
	return out
}

// reorder puts flags before positional arguments, so `sdna test run flows/ --env dev` works.
func reorder(args []string) []string {
	var flags, rest []string
	for i := 0; i < len(args); i++ {
		if strings.HasPrefix(args[i], "-") {
			flags = append(flags, args[i])
			if !strings.Contains(args[i], "=") && i+1 < len(args) && !strings.HasPrefix(args[i+1], "-") {
				flags = append(flags, args[i+1])
				i++
			}
		} else {
			rest = append(rest, args[i])
		}
	}
	return append(flags, rest...)
}

func nilIfEmpty(s string) any {
	if s == "" {
		return nil
	}
	return s
}

// markdownReport renders suite results for a CI job summary or PR comment. With appURL (the
// ServiceDNA web app), each case links to its trace.
func markdownReport(suites []suite, appURL string) string {
	var b strings.Builder
	passed, failed := 0, 0
	for _, s := range suites {
		passed += s.Passed
		failed += s.Failed
	}
	icon := "✅"
	if failed > 0 {
		icon = "❌"
	}
	fmt.Fprintf(&b, "### %s ServiceDNA test flows — %d passed, %d failed\n\n", icon, passed, failed)
	for _, s := range suites {
		fmt.Fprintf(&b, "**%s**\n\n| | Case | Checks that failed |\n|---|---|---|\n", s.Name)
		for _, run := range s.Runs {
			mark := "✅"
			if run.Passed == nil || !*run.Passed {
				mark = "❌"
			}
			name := strings.ReplaceAll(run.CaseName, "|", "\\|")
			if appURL != "" && run.TraceID != "" {
				name = fmt.Sprintf("[%s](%s/traces?trace=%s)", name, strings.TrimRight(appURL, "/"), run.TraceID)
			}
			var problems []string
			for _, r := range run.Results {
				if !r.Passed {
					problems = append(problems, strings.ReplaceAll(r.Description+" — "+r.Message, "|", "\\|"))
				}
			}
			fmt.Fprintf(&b, "| %s | %s | %s |\n", mark, name, strings.Join(problems, "<br>"))
		}
		b.WriteString("\n")
	}
	return b.String()
}
