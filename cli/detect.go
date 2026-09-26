package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// Stack is a project type sdna init knows how to set up.
type Stack struct {
	Name    string
	Service string // default service name, from the project's own metadata
	Install []string
	Run     string   // how to start the service with ServiceDNA
	Notes   []string // manual steps that can't be automated safely
}

// detectStack inspects a project directory. Checked in order, so a Node frontend inside a Java
// repo is still detected as Java when pom.xml sits at the root.
func detectStack(dir string) (*Stack, bool) {
	exists := func(name string) bool {
		_, err := os.Stat(filepath.Join(dir, name))
		return err == nil
	}
	base := filepath.Base(dir)
	switch {
	case exists("pom.xml") || exists("build.gradle") || exists("build.gradle.kts"):
		name := firstMatch(dir, []string{"src/main/resources/application.properties", "src/main/resources/application.yml", "src/main/resources/application.yaml"},
			regexp.MustCompile(`(?m)^\s*(?:spring\.application\.)?name\s*[:=]\s*([\w.-]+)\s*$`))
		return &Stack{
			Name:    "spring-boot",
			Service: orDefault(name, base),
			Run:     "./mvnw spring-boot:run   # or your usual command",
			Notes: []string{
				"Add to pom.xml <dependencies>:\n  <dependency>\n    <groupId>io.github.varuns2903</groupId>\n    <artifactId>servicedna-spring-boot-starter</artifactId>\n    <version>0.1.0</version>\n  </dependency>",
				"Add to pom.xml <properties> (Spring Boot manages an older OpenTelemetry):\n  <opentelemetry.version>1.65.0</opentelemetry.version>",
			},
		}, true
	case exists("go.mod"):
		return &Stack{
			Name:    "go",
			Service: orDefault(goModuleName(dir), base),
			Install: []string{"go", "get", "github.com/varuns2903/servicedna/sdks/go"},
			Run:     "go run .",
			Notes: []string{
				"In main():\n  shutdown, err := servicedna.Start(ctx)\n  defer shutdown(context.Background())\nand serve with sdnahttp.Handler(mux).",
			},
		}, true
	case exists("package.json"):
		return &Stack{
			Name:    "node",
			Service: orDefault(packageName(dir), base),
			Install: []string{"npm", "install", "@servicedna/node"},
			Run:     "node -r @servicedna/node/register <your entry file>",
		}, true
	case exists("pyproject.toml") || exists("requirements.txt") || exists("setup.py"):
		return &Stack{
			Name:    "python",
			Service: base,
			Install: []string{"pip", "install", "servicedna"},
			Run:     "servicedna-run <your usual command, e.g. uvicorn app.main:app>",
		}, true
	}
	return nil, false
}

func packageName(dir string) string {
	data, err := os.ReadFile(filepath.Join(dir, "package.json"))
	if err != nil {
		return ""
	}
	var pkg struct {
		Name string `json:"name"`
	}
	_ = json.Unmarshal(data, &pkg)
	if i := strings.LastIndex(pkg.Name, "/"); i >= 0 {
		return pkg.Name[i+1:]
	}
	return pkg.Name
}

func goModuleName(dir string) string {
	data, err := os.ReadFile(filepath.Join(dir, "go.mod"))
	if err != nil {
		return ""
	}
	m := regexp.MustCompile(`(?m)^module\s+(\S+)`).FindStringSubmatch(string(data))
	if m == nil {
		return ""
	}
	return filepath.Base(m[1])
}

func firstMatch(dir string, files []string, re *regexp.Regexp) string {
	for _, f := range files {
		data, err := os.ReadFile(filepath.Join(dir, f))
		if err != nil {
			continue
		}
		if m := re.FindStringSubmatch(string(data)); m != nil {
			return m[1]
		}
	}
	return ""
}

func orDefault(v, fallback string) string {
	if v != "" {
		return v
	}
	return fallback
}
