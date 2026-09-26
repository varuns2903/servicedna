package main

import (
	"encoding/json"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"
)

// Operation is one entry in a service's API catalog.
type Operation struct {
	Protocol      string `json:"protocol"`
	Name          string `json:"name"`
	Source        string `json:"source"`
	Description   string `json:"description,omitempty"`
	RequestSchema string `json:"requestSchema,omitempty"`
}

// ScanResult is what `sdna scan` found in a directory.
type ScanResult struct {
	Operations []Operation
	// Service names referenced from this repository's configuration (URLs like http://payments:8080).
	References []string
	// For docker-compose files: each compose service's references, keyed by its compose name.
	ComposeReferences map[string][]string
	Files             []string
}

var skipDirs = map[string]bool{
	".git": true, "node_modules": true, "vendor": true, "third_party": true, "dist": true,
	"build": true, "target": true, ".venv": true, "venv": true, "__pycache__": true, ".idea": true,
}

// scanDir walks a repository for API specs and configuration that names other services.
func scanDir(root string) (*ScanResult, error) {
	result := &ScanResult{ComposeReferences: map[string][]string{}}
	references := map[string]bool{}
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() {
			if skipDirs[d.Name()] && path != root {
				return filepath.SkipDir
			}
			return nil
		}
		info, err := d.Info()
		if err != nil || info.Size() > 2<<20 {
			return nil
		}
		name := strings.ToLower(d.Name())
		rel, _ := filepath.Rel(root, path)
		switch {
		case strings.HasSuffix(name, ".proto"):
			data, _ := os.ReadFile(path)
			if ops := parseProto(string(data)); len(ops) > 0 {
				result.Operations = append(result.Operations, ops...)
				result.Files = append(result.Files, rel)
			}
		case strings.HasSuffix(name, ".yaml") || strings.HasSuffix(name, ".yml") || strings.HasSuffix(name, ".json"):
			data, _ := os.ReadFile(path)
			var doc map[string]any
			if strings.HasSuffix(name, ".json") {
				if json.Unmarshal(data, &doc) != nil {
					return nil
				}
			} else if yaml.Unmarshal(data, &doc) != nil {
				return nil
			}
			switch {
			case doc["openapi"] != nil || doc["swagger"] != nil:
				result.Operations = append(result.Operations, parseOpenAPI(doc)...)
				result.Files = append(result.Files, rel)
			case doc["asyncapi"] != nil:
				result.Operations = append(result.Operations, parseAsyncAPI(doc)...)
				result.Files = append(result.Files, rel)
			case strings.HasPrefix(name, "docker-compose") || strings.HasPrefix(name, "compose."):
				for service, refs := range composeReferences(doc) {
					result.ComposeReferences[service] = refs
				}
				result.Files = append(result.Files, rel)
			case isConfigFile(rel):
				for _, host := range urlHosts(string(data)) {
					references[host] = true
				}
			}
		case isConfigFile(rel):
			data, _ := os.ReadFile(path)
			for _, host := range urlHosts(string(data)) {
				references[host] = true
			}
		}
		return nil
	})
	result.References = sortedKeys(references)
	return result, err
}

// isConfigFile: .env files, Spring application config, properties, and manifests in deploy dirs.
func isConfigFile(rel string) bool {
	base := strings.ToLower(filepath.Base(rel))
	if base == ".env" || strings.HasPrefix(base, ".env.") && base != ".env.servicedna" {
		return true
	}
	if strings.HasPrefix(base, "application") && (strings.HasSuffix(base, ".yml") || strings.HasSuffix(base, ".yaml") || strings.HasSuffix(base, ".properties")) {
		return true
	}
	for _, dir := range []string{"k8s", "kubernetes", "deploy", "manifests", "helm", "charts"} {
		if strings.Contains("/"+filepath.ToSlash(strings.ToLower(rel)), "/"+dir+"/") {
			return true
		}
	}
	return false
}

var urlHost = regexp.MustCompile(`(?i)\b(?:https?|grpc|kafka|amqp|redis)://([a-z0-9][a-z0-9-]*[a-z0-9])(?:[.:/]|\b)`)

// urlHosts returns the first DNS label of every URL host in the text: in Docker and Kubernetes,
// that's the service name.
func urlHosts(text string) []string {
	seen := map[string]bool{}
	for _, m := range urlHost.FindAllStringSubmatch(text, -1) {
		host := strings.ToLower(m[1])
		if host != "localhost" && !regexp.MustCompile(`^\d+$`).MatchString(host) {
			seen[host] = true
		}
	}
	return sortedKeys(seen)
}

// composeReferences maps each compose service to the hosts its environment URLs point at.
func composeReferences(doc map[string]any) map[string][]string {
	out := map[string][]string{}
	services, _ := doc["services"].(map[string]any)
	for name, raw := range services {
		svc, _ := raw.(map[string]any)
		env := svc["environment"]
		var text strings.Builder
		switch e := env.(type) {
		case map[string]any:
			for _, v := range e {
				if s, ok := v.(string); ok {
					text.WriteString(s + "\n")
				}
			}
		case []any:
			for _, v := range e {
				if s, ok := v.(string); ok {
					text.WriteString(s + "\n")
				}
			}
		}
		var refs []string
		for _, host := range urlHosts(text.String()) {
			if host != name {
				refs = append(refs, host)
			}
		}
		if len(refs) > 0 {
			out[name] = refs
		}
	}
	return out
}

func parseOpenAPI(doc map[string]any) []Operation {
	var ops []Operation
	paths, _ := doc["paths"].(map[string]any)
	for path, raw := range paths {
		methods, _ := raw.(map[string]any)
		for method, rawOp := range methods {
			m := strings.ToUpper(method)
			if !map[string]bool{"GET": true, "POST": true, "PUT": true, "PATCH": true, "DELETE": true, "HEAD": true, "OPTIONS": true}[m] {
				continue
			}
			op, _ := rawOp.(map[string]any)
			description, _ := op["summary"].(string)
			if description == "" {
				description, _ = op["description"].(string)
			}
			var schema string
			if body, ok := op["requestBody"].(map[string]any); ok {
				if content, ok := body["content"].(map[string]any); ok {
					if media, ok := content["application/json"].(map[string]any); ok && media["schema"] != nil {
						schema = toJSON(resolveRefs(doc, media["schema"], 0))
					}
				}
			}
			ops = append(ops, Operation{Protocol: "HTTP", Name: m + " " + path, Source: "OPENAPI", Description: description, RequestSchema: schema})
		}
	}
	sortOps(ops)
	return ops
}

func parseAsyncAPI(doc map[string]any) []Operation {
	var ops []Operation
	channels, _ := doc["channels"].(map[string]any)
	if operations, ok := doc["operations"].(map[string]any); ok { // AsyncAPI 3
		for _, raw := range operations {
			op, _ := raw.(map[string]any)
			action, _ := op["action"].(string)
			channel := ""
			if ref, ok := op["channel"].(map[string]any); ok {
				if r, ok := ref["$ref"].(string); ok {
					key := r[strings.LastIndex(r, "/")+1:]
					if ch, ok := channels[key].(map[string]any); ok {
						channel, _ = ch["address"].(string)
					}
					if channel == "" {
						channel = key
					}
				}
			}
			verb := map[string]string{"send": "publish", "receive": "consume"}[action]
			if verb != "" && channel != "" {
				ops = append(ops, Operation{Protocol: "MESSAGING", Name: verb + " " + channel, Source: "ASYNCAPI"})
			}
		}
	} else { // AsyncAPI 2: "subscribe" = the app sends, "publish" = the app receives
		for channel, raw := range channels {
			ch, _ := raw.(map[string]any)
			for key, verb := range map[string]string{"subscribe": "publish", "publish": "consume"} {
				op, ok := ch[key].(map[string]any)
				if !ok {
					continue
				}
				var schema string
				if msg, ok := op["message"].(map[string]any); ok && msg["payload"] != nil {
					schema = toJSON(resolveRefs(doc, msg["payload"], 0))
				}
				description, _ := op["summary"].(string)
				ops = append(ops, Operation{Protocol: "MESSAGING", Name: verb + " " + channel, Source: "ASYNCAPI", Description: description, RequestSchema: schema})
			}
		}
	}
	sortOps(ops)
	return ops
}

var (
	protoPackage = regexp.MustCompile(`(?m)^\s*package\s+([\w.]+)\s*;`)
	protoService = regexp.MustCompile(`(?s)service\s+(\w+)\s*\{(.*?)\n\}`)
	protoRPC     = regexp.MustCompile(`rpc\s+(\w+)\s*\(\s*(?:stream\s+)?([\w.]+)\s*\)`)
	protoMessage = regexp.MustCompile(`(?s)message\s+(\w+)\s*\{(.*?)\n\}`)
	protoField   = regexp.MustCompile(`(?m)^\s*(?:repeated\s+|optional\s+)?([\w.]+)\s+(\w+)\s*=\s*\d+`)
)

// parseProto finds gRPC methods as "package.Service/Method", with a JSON Schema sketched from the
// request message's fields.
func parseProto(src string) []Operation {
	pkg := ""
	if m := protoPackage.FindStringSubmatch(src); m != nil {
		pkg = m[1] + "."
	}
	messages := map[string]string{}
	for _, m := range protoMessage.FindAllStringSubmatch(src, -1) {
		props := map[string]any{}
		for _, f := range protoField.FindAllStringSubmatch(m[2], -1) {
			props[f[2]] = map[string]any{"type": protoJSONType(f[1])}
		}
		messages[m[1]] = toJSON(map[string]any{"type": "object", "properties": props})
	}
	var ops []Operation
	for _, s := range protoService.FindAllStringSubmatch(src, -1) {
		for _, rpc := range protoRPC.FindAllStringSubmatch(s[2], -1) {
			request := rpc[2][strings.LastIndex(rpc[2], ".")+1:]
			ops = append(ops, Operation{Protocol: "GRPC", Name: pkg + s[1] + "/" + rpc[1], Source: "PROTO", RequestSchema: messages[request]})
		}
	}
	return ops
}

func protoJSONType(t string) string {
	switch t {
	case "string", "bytes":
		return "string"
	case "bool":
		return "boolean"
	case "double", "float":
		return "number"
	case "int32", "int64", "uint32", "uint64", "sint32", "sint64", "fixed32", "fixed64", "sfixed32", "sfixed64":
		return "integer"
	}
	return "object"
}

// resolveRefs inlines local "$ref"s ("#/components/schemas/Order") so a schema stands alone.
func resolveRefs(doc map[string]any, node any, depth int) any {
	if depth > 10 {
		return node
	}
	switch n := node.(type) {
	case map[string]any:
		if ref, ok := n["$ref"].(string); ok && strings.HasPrefix(ref, "#/") {
			var target any = doc
			for _, part := range strings.Split(ref[2:], "/") {
				m, ok := target.(map[string]any)
				if !ok {
					return n
				}
				target = m[part]
			}
			return resolveRefs(doc, target, depth+1)
		}
		out := map[string]any{}
		for k, v := range n {
			out[k] = resolveRefs(doc, v, depth+1)
		}
		return out
	case []any:
		out := make([]any, len(n))
		for i, v := range n {
			out[i] = resolveRefs(doc, v, depth+1)
		}
		return out
	}
	return node
}

func toJSON(v any) string {
	data, err := json.Marshal(v)
	if err != nil {
		return ""
	}
	return string(data)
}

func sortOps(ops []Operation) {
	sort.Slice(ops, func(i, j int) bool { return ops[i].Name < ops[j].Name })
}

func sortedKeys(m map[string]bool) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
