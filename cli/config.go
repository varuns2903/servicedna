package main

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
)

// Config is what `sdna login` and `sdna use` remember, stored with owner-only permissions.
type Config struct {
	URL          string `json:"url"`
	Email        string `json:"email"`
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken"`
	OrgID        string `json:"orgId"`
	OrgName      string `json:"orgName"`
}

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
		return &Config{}, nil
	}
	if err != nil {
		return nil, err
	}
	var c Config
	return &c, json.Unmarshal(data, &c)
}

func (c *Config) save() error {
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
