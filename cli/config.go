package main

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// Config is what `sdna login` and `sdna use` remember, stored with owner-only permissions.
type Config struct {
	URL          string `json:"url"`
	Email        string `json:"email"`
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken"`
	OrgID        string `json:"orgId"`
	OrgName      string `json:"orgName"`
	// fromEnv: settings came from SDNA_TOKEN (CI); they're never written to the config file.
	fromEnv bool
}

// applyEnv lets CI run without `sdna login`: SDNA_URL, SDNA_TOKEN (an API token, sdna_pat_…) and
// SDNA_ORG (organization id or name) override the config file.
func (c *Config) applyEnv(getenv func(string) string) {
	if u := getenv("SDNA_URL"); u != "" {
		c.URL = strings.TrimRight(u, "/")
	}
	if t := getenv("SDNA_TOKEN"); t != "" {
		c.Token, c.RefreshToken, c.fromEnv = t, "", true
	}
	if o := getenv("SDNA_ORG"); o != "" {
		if uuidPattern.MatchString(o) {
			c.OrgID, c.OrgName = o, ""
		} else {
			c.OrgID, c.OrgName = "", o
		}
	}
}

var uuidPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

func configPath() (string, error) {
	if p := os.Getenv("SDNA_CONFIG"); p != "" {
		return p, nil
	}
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "servicedna", "config.json"), nil
}

func loadConfig() (*Config, error) {
	path, err := configPath()
	if err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		c := &Config{}
		c.applyEnv(os.Getenv)
		return c, nil
	}
	if err != nil {
		return nil, err
	}
	var c Config
	if err := json.Unmarshal(data, &c); err != nil {
		return nil, err
	}
	c.applyEnv(os.Getenv)
	return &c, nil
}

func (c *Config) save() error {
	if c.fromEnv {
		return nil
	}
	path, err := configPath()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o600)
}
