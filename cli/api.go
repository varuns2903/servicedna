package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// Client talks to the ServiceDNA API as the logged-in user, refreshing the session when the access
// token expires.
type Client struct {
	cfg  *Config
	http *http.Client
}

func newClient(cfg *Config) *Client {
	return &Client{cfg: cfg, http: &http.Client{Timeout: 30 * time.Second}}
}

// APIError is ServiceDNA's error envelope.
type APIError struct {
	Status    int
	ErrorCode string `json:"errorCode"`
	Message   string `json:"message"`
}

func (e *APIError) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return fmt.Sprintf("HTTP %d", e.Status)
}

func (c *Client) do(method, path string, body, out any) error {
	err := c.request(method, path, body, out)
	// An expired access token is a bare 403 with no errorCode; refresh once and retry.
	if apiErr, ok := err.(*APIError); ok && apiErr.Status == http.StatusForbidden && apiErr.ErrorCode == "" && c.cfg.RefreshToken != "" {
		if refreshErr := c.refresh(); refreshErr != nil {
			return fmt.Errorf("session expired; run `sdna login` again")
		}
		return c.request(method, path, body, out)
	}
	return err
}

func (c *Client) request(method, path string, body, out any) error {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(data)
	}
	req, err := http.NewRequest(method, strings.TrimRight(c.cfg.URL, "/")+"/api/v1"+path, reader)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if c.cfg.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.cfg.Token)
	}
	res, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("can't reach ServiceDNA at %s: %w", c.cfg.URL, err)
	}
	defer res.Body.Close()
	data, _ := io.ReadAll(res.Body)
	if res.StatusCode >= 300 {
		apiErr := &APIError{Status: res.StatusCode}
		_ = json.Unmarshal(data, apiErr)
		return apiErr
	}
	if out != nil && len(data) > 0 {
		return json.Unmarshal(data, out)
	}
	return nil
}

type authResponse struct {
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken"`
	User         struct {
		Email string `json:"email"`
	} `json:"user"`
}

func (c *Client) login(email, password string) error {
	var auth authResponse
	if err := c.request(http.MethodPost, "/auth/login", map[string]string{"email": email, "password": password}, &auth); err != nil {
		return err
	}
	c.cfg.Token, c.cfg.RefreshToken, c.cfg.Email = auth.Token, auth.RefreshToken, auth.User.Email
	return c.cfg.save()
}

func (c *Client) refresh() error {
	var auth authResponse
	token := c.cfg.Token
	c.cfg.Token = ""
	if err := c.request(http.MethodPost, "/auth/refresh", map[string]string{"refreshToken": c.cfg.RefreshToken}, &auth); err != nil {
		c.cfg.Token = token
		return err
	}
	c.cfg.Token, c.cfg.RefreshToken = auth.Token, auth.RefreshToken
	return c.cfg.save()
}

// API shapes the CLI reads.

type Organization struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type Service struct {
	ID              string  `json:"id"`
	Name            string  `json:"name"`
	Status          string  `json:"status"`
	Environment     *string `json:"environment"`
	Language        *string `json:"language"`
	Version         *string `json:"version"`
	LastTelemetryAt *string `json:"lastTelemetryAt"`
}

type Incident struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	Severity  string `json:"severity"`
	Status    string `json:"status"`
	CreatedAt string `json:"createdAt"`
}

type IngestionKey struct {
	ID         string  `json:"id"`
	Name       string  `json:"name"`
	KeyPrefix  string  `json:"keyPrefix"`
	Key        *string `json:"key"`
	LastUsedAt *string `json:"lastUsedAt"`
	RevokedAt  *string `json:"revokedAt"`
}
