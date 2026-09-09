<div align="center">

<img src="docs/images/banner.jpg" alt="ServiceDNA Dashboard" width="100%" />

# 🧬 ServiceDNA

**The open-source service health, dependency, and incident management platform.**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.0-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-7.5-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

[Features](#-features) · [Architecture](#-architecture) · [Quick Start](#-quick-start) · [API Docs](API.md) · [Contributing](CONTRIBUTING.md)

</div>

---

## 🚀 What is ServiceDNA?

ServiceDNA is a full-stack platform that gives engineering teams **complete visibility** into their distributed services. Register your microservices, monitor their health with automated checks, visualize inter-service dependencies as an interactive graph, and manage incidents end-to-end — all from a single, real-time dashboard.

Think of it as a self-hosted blend of **Datadog's Service Map**, **PagerDuty's Incident Management**, and **Atlassian Statuspage** — built with a modern stack and designed for developers who want full control.

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 📊 Real-Time Dashboard
Live health overview of all services with instant WebSocket updates. No refresh needed — metrics stream directly to your browser via STOMP over SockJS.

### 🔗 Interactive Dependency Graph
Visualize service-to-service relationships with an auto-layouted directed graph. Click any node to explore blast radius and upstream/downstream dependencies.

### 🚨 Incident Management
Full lifecycle from detection to resolution. Create incidents manually or let the alert engine generate them automatically. Includes post-mortem notes and severity tracking.

</td>
<td width="50%">

### 🔔 Configurable Alert Rules
Define per-service alert rules based on latency thresholds, error rates, or consecutive health check failures. Alerts automatically trigger incident creation.

### 🌐 Public Status Page
Share a read-only, unauthenticated status page (`/status/:orgId`) with your customers. Auto-refreshes every 30 seconds with live service health and active incidents.

### 💳 Stripe Billing Integration
Built-in subscription management with Free, Pro, and Enterprise tiers. Checkout sessions, plan upgrades, and billing portal — all wired through Stripe's API.

</td>
</tr>
</table>

### And also...

- 🔐 **GitHub OAuth2 Authentication** — Passwordless login with stateless JWT sessions
- 🏢 **Multi-Organization Support** — Isolated tenants with role-based member management and email invites
- ⌨️ **Command Palette** (`Ctrl+K`) — Instant fuzzy search across services, incidents, and navigation
- 🧪 **E2E Testing** — Playwright test suite for critical navigation paths
- 📡 **Event-Driven Architecture** — Kafka-powered async processing for health checks, alerts, and telemetry

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    FRONTEND (React SPA)                  │
│  Vite · TypeScript · Tailwind · TanStack Query · Zustand│
│  React Flow · cmdk · STOMP/SockJS · Playwright          │
└────────────────────────┬────────────────────────────────┘
                         │ REST + WebSocket
┌────────────────────────▼────────────────────────────────┐
│                 BACKEND (Spring Boot 3.x)                │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌───────────┐ │
│  │   Auth   │ │ Service  │ │ Incident  │ │  Billing  │ │
│  │ (OAuth2) │ │ Registry │ │  Manager  │ │ (Stripe)  │ │
│  └──────────┘ └──────────┘ └───────────┘ └───────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌───────────┐ │
│  │  Alert   │ │Dashboard │ │ Telemetry │ │   Org &   │ │
│  │  Engine  │ │  & WS    │ │  Ingest   │ │  Members  │ │
│  └──────────┘ └──────────┘ └───────────┘ └───────────┘ │
└───┬──────────────┬──────────────┬───────────────┬───────┘
    │              │              │               │
┌───▼───┐   ┌─────▼─────┐  ┌────▼────┐   ┌──────▼──────┐
│Postgres│   │   Redis   │  │  Kafka  │   │   Zipkin    │
│  (DB)  │   │  (Cache)  │  │ (Events)│   │ (Tracing)   │
└────────┘   └───────────┘  └─────────┘   └─────────────┘
```

### Backend Modules

| Module | Description |
|--------|-------------|
| `auth` | GitHub OAuth2 login, JWT issuance, `/me` endpoint |
| `organization` | Multi-tenant org management, members, role-based access |
| `service` | Service registry, health checks, dependency graph, public status |
| `incident` | Incident CRUD, status transitions, post-mortems |
| `alert` | Alert rule configuration, Kafka consumer for threshold evaluation |
| `dashboard` | Aggregated summary, WebSocket broadcast via STOMP |
| `billing` | Stripe subscriptions, checkout sessions, webhook handler |
| `telemetry` | Metrics ingestion and analytics |
| `common` | Shared config (CORS, WebSocket, security) |

### Frontend Stack

| Layer | Technology |
|-------|-----------|
| Framework | React 19 + TypeScript 6 |
| Build | Vite 8 |
| Styling | Tailwind CSS 4 (dark-first charcoal theme) |
| Server State | TanStack Query (caching, background refetch) |
| Client State | Zustand (auth, org selection, UI) |
| Real-Time | STOMP over SockJS → TanStack Query cache invalidation |
| Graph | React Flow + dagre auto-layout |
| Command Palette | cmdk |
| Testing | Playwright (Chromium) |

---

## ⚡ Quick Start

### Prerequisites

- **Java 21** (JDK)
- **Node.js 20+** & npm
- **Docker** & Docker Compose
- A [GitHub OAuth App](https://github.com/settings/developers) (for authentication)

### 1. Clone the Repository

```bash
git clone https://github.com/varuns2903/servicedna.git
cd servicedna
```

### 2. Start Infrastructure

```bash
docker-compose up -d
```

This starts **PostgreSQL**, **Redis**, **Kafka** (with Zookeeper), and **Zipkin**.

### 3. Configure the Backend

```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env` and fill in your:
- `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` — from your GitHub OAuth App
- `JWT_SECRET` — any strong 256-bit random string
- `STRIPE_API_KEY` — your Stripe test secret key (optional)

### 4. Start the Backend

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 5. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at `http://localhost:5173`.

### 6. Run E2E Tests (Optional)

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

---

## 📁 Project Structure

```
servicedna/
├── backend/
│   ├── src/main/java/com/servicedna/
│   │   ├── alert/          # Alert rules & Kafka consumer
│   │   ├── analytics/      # Metrics & analytics
│   │   ├── auth/           # OAuth2, JWT, security config
│   │   ├── billing/        # Stripe integration
│   │   ├── dashboard/      # Dashboard summary & WebSocket publisher
│   │   ├── incident/       # Incident lifecycle management
│   │   ├── organization/   # Multi-tenant org & members
│   │   ├── service/        # Service registry & health checks
│   │   ├── telemetry/      # Telemetry ingestion
│   │   └── common/         # CORS, WebSocket, shared config
│   └── src/main/resources/
│       └── db/migration/   # 16 Flyway migrations
├── frontend/
│   ├── src/
│   │   ├── api/            # Typed Axios API clients
│   │   ├── components/     # Design system (UI primitives, layout)
│   │   ├── features/       # Feature modules (dashboard, services, map, etc.)
│   │   ├── hooks/          # TanStack Query hooks
│   │   ├── providers/      # WebSocket provider
│   │   └── stores/         # Zustand stores (auth, org, UI)
│   ├── tests/              # Playwright E2E tests
│   └── playwright.config.ts
├── docker-compose.yml      # Local infrastructure
├── API.md                  # API reference
├── ARCHITECTURE.md         # System architecture docs
├── CONTRIBUTING.md         # Contribution guidelines
└── SECURITY.md             # Security policy
```

---

## 🔑 Environment Variables

<details>
<summary><strong>Backend</strong> (<code>backend/.env</code>)</summary>

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5433/servicedna` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `postgres` |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6380` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID | — |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Client Secret | — |
| `JWT_SECRET` | JWT signing key (256-bit min) | — |
| `JWT_EXPIRATION_MS` | Token TTL in ms | `86400000` |
| `STRIPE_API_KEY` | Stripe secret key | — |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook verification | — |

</details>

<details>
<summary><strong>Frontend</strong> (<code>frontend/.env</code>)</summary>

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_URL` | Backend API base URL | `http://localhost:8080/api/v1` |

</details>

---

## 🤝 Contributing

We welcome contributions! Please read our [Contributing Guide](CONTRIBUTING.md) for details on the development workflow, coding standards, and how to submit pull requests.

## 🔒 Security

If you discover a security vulnerability, please report it responsibly. See our [Security Policy](SECURITY.md) for details.

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Built with ☕ and 🧬 by [varuns2903](https://github.com/varuns2903)

</div>
