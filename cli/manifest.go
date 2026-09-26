package main

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

// Manifest is a repository's servicedna.yaml: what ServiceDNA can't learn from traffic.
//
//	service: order-service
//	owner: team-checkout
//	tier: critical            # critical | high | medium | low
//	slo: 99.9
//	health: http://order-service:4004/health
//	dependencies: [payment-service]   # optional: the observed graph fills in the rest
//	alerts:
//	  - condition: STATUS_DOWN
//	    open_incident: CRITICAL
type Manifest struct {
	Service      string          `yaml:"service"`
	Description  string          `yaml:"description"`
	Owner        string          `yaml:"owner"`
	Tier         string          `yaml:"tier"`
	SLO          *float64        `yaml:"slo"`
	Health       string          `yaml:"health"`
	Repository   string          `yaml:"repository"`
	Dependencies []string        `yaml:"dependencies"`
	Alerts       *[]ManifestRule `yaml:"alerts"`
}

// ManifestRule is one alert rule: a condition plus at least one action (open_incident, webhook).
type ManifestRule struct {
	Condition     string   `yaml:"condition"`
	OpenIncident  string   `yaml:"open_incident"`
	Webhook       string   `yaml:"webhook"`
	Integration   string   `yaml:"integration"`
	Threshold     *float64 `yaml:"threshold"`
	WindowMinutes *int     `yaml:"window_minutes"`
}

type scanMetadata struct {
	Description   string   `json:"description,omitempty"`
	Owner         string   `json:"owner,omitempty"`
	Tier          string   `json:"tier,omitempty"`
	SLO           *float64 `json:"slo,omitempty"`
	HealthURL     string   `json:"healthUrl,omitempty"`
	RepositoryURL string   `json:"repositoryUrl,omitempty"`
}

type alertRule struct {
	Condition        string   `json:"condition"`
	IncidentSeverity string   `json:"incidentSeverity,omitempty"`
	WebhookURL       string   `json:"webhookUrl,omitempty"`
	IntegrationType  string   `json:"integrationType,omitempty"`
	Threshold        *float64 `json:"threshold,omitempty"`
	WindowMinutes    *int     `json:"windowMinutes,omitempty"`
}

var manifestNames = []string{"servicedna.yaml", "servicedna.yml"}

// loadManifest reads servicedna.yaml from dir; nil (and no error) when there is none. Unknown keys
// are errors, so a typo doesn't silently do nothing.
func loadManifest(dir string) (*Manifest, string, error) {
	for _, name := range manifestNames {
		data, err := os.ReadFile(filepath.Join(dir, name))
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return nil, name, err
		}
		dec := yaml.NewDecoder(bytes.NewReader(data))
		dec.KnownFields(true)
		var m Manifest
		if err := dec.Decode(&m); err != nil {
			return nil, name, fmt.Errorf("%s: %w", name, err)
		}
		return &m, name, m.validate(name)
	}
	return nil, "", nil
}

func (m *Manifest) validate(file string) error {
	var problems []string
	switch m.Tier {
	case "", "critical", "high", "medium", "low":
	default:
		problems = append(problems, fmt.Sprintf("tier %q isn't critical, high, medium or low", m.Tier))
	}
	if m.SLO != nil && (*m.SLO <= 0 || *m.SLO > 100) {
		problems = append(problems, "slo is a percentage, e.g. 99.9")
	}
	if m.Health != "" && !strings.HasPrefix(m.Health, "http://") && !strings.HasPrefix(m.Health, "https://") {
		problems = append(problems, "health is the full URL ServiceDNA probes, e.g. http://orders:4004/health")
	}
	if m.Alerts != nil {
		for i, r := range *m.Alerts {
			if r.Condition == "" {
				problems = append(problems, fmt.Sprintf("alerts[%d] needs a condition", i))
			}
			if r.OpenIncident == "" && r.Webhook == "" {
				problems = append(problems, fmt.Sprintf("alerts[%d] needs open_incident, webhook, or both", i))
			}
		}
	}
	if len(problems) > 0 {
		return fmt.Errorf("%s: %s", file, strings.Join(problems, "; "))
	}
	return nil
}

func (m *Manifest) metadata() *scanMetadata {
	md := &scanMetadata{Description: m.Description, Owner: m.Owner, Tier: m.Tier, SLO: m.SLO, HealthURL: m.Health, RepositoryURL: m.Repository}
	if *md == (scanMetadata{}) {
		return nil
	}
	return md
}

// alerts is nil when the file has no alerts key (rules are left alone), and an empty list for
// `alerts: []` (the rules it managed are removed).
func (m *Manifest) alerts() []alertRule {
	if m.Alerts == nil {
		return nil
	}
	rules := []alertRule{}
	for _, r := range *m.Alerts {
		rules = append(rules, alertRule{
			Condition: strings.ToUpper(r.Condition), IncidentSeverity: strings.ToUpper(r.OpenIncident),
			WebhookURL: r.Webhook, IntegrationType: strings.ToUpper(r.Integration),
			Threshold: r.Threshold, WindowMinutes: r.WindowMinutes,
		})
	}
	return rules
}

// manifestTemplate is what `sdna init` writes when a repository has no servicedna.yaml yet.
func manifestTemplate(service string) string {
	return fmt.Sprintf(`# ServiceDNA catalog entry — what traffic can't tell ServiceDNA. Applied by `+"`sdna scan`"+`.
service: %s
# description: What this service does
# owner: team-name
tier: medium            # critical | high | medium | low
slo: 99.9
# health: http://%s:8080/health   # the URL ServiceDNA probes
dependencies: []        # optional: calls seen in traffic are added automatically
alerts:
  - condition: STATUS_DOWN
    open_incident: MAJOR
`, service, service)
}
