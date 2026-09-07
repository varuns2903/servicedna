# API Conventions

ServiceDNA follows RESTful conventions.

## Base URL
`/api/v1`

## Common Response Structure
Errors return a standard envelope:
```json
{
  "timestamp": "2024-03-12T12:00:00Z",
  "status": 400,
  "errorCode": "INVALID_REQUEST",
  "message": "The provided service URL is invalid.",
  "path": "/api/v1/services",
  "requestId": "req-12345"
}
```

## Endpoints (Proposed)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/services`
- `POST /api/v1/services`
- `GET /api/v1/services/{id}/health`
