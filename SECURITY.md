# Security

## Authentication & Authorization
- Uses JWT access tokens.
- Role-based Access Control (OWNER, ADMIN, MEMBER, VIEWER).
- Organization isolation for all resources.

## SSRF Protection
Since ServiceDNA calls user-provided URLs for health checks, we must prevent Server-Side Request Forgery.
- Deny resolutions to `localhost`, `127.0.0.1`, and private IP ranges (e.g., `10.0.0.0/8`, `192.168.0.0/16`).
- Refuse redirects to internal network addresses.
- Prevent access to cloud metadata endpoints (e.g., `169.254.169.254`).

## General Hardening
- Passwords hashed using bcrypt or Argon2.
- CORS policies properly configured.
- Rate limiting to prevent brute-forcing endpoints.
