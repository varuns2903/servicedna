// Package sdnahttp traces net/http servers and clients for ServiceDNA.
package sdnahttp

import (
	"net/http"
	"net/url"

	servicedna "github.com/varuns2903/servicedna/sdks/go"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

// Handler traces every incoming request, named "METHOD /route" from Go 1.22 ServeMux patterns.
// Requests to the service's health path aren't traced.
func Handler(h http.Handler) http.Handler {
	healthPath := ""
	if u, err := url.Parse(servicedna.Current().LocalHealthURL); err == nil {
		healthPath = u.Path
	}
	return otelhttp.NewHandler(h, "http.server",
		otelhttp.WithFilter(func(r *http.Request) bool { return healthPath == "" || r.URL.Path != healthPath }),
		otelhttp.WithSpanNameFormatter(func(_ string, r *http.Request) string {
			if r.Pattern != "" {
				return r.Pattern
			}
			return r.Method
		}),
	)
}

// Transport traces outgoing requests and propagates trace context to the services they call.
func Transport(base http.RoundTripper) http.RoundTripper {
	if base == nil {
		base = http.DefaultTransport
	}
	return otelhttp.NewTransport(base)
}

// Client is an http.Client whose requests are traced.
func Client() *http.Client {
	return &http.Client{Transport: Transport(nil)}
}
