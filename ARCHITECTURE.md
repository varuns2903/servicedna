# Architecture

ServiceDNA follows a modular Spring Boot architecture.

## Overview
```text
                 ┌─────────────────┐
                 │ React Dashboard │
                 └────────┬────────┘
                          │
                     REST/WebSocket
                          │
                 ┌────────▼────────┐
                 │ Spring Boot API │
                 └────────┬────────┘
                          │
          ┌───────────────┼────────────────┐
          │               │                │
          ▼               ▼                ▼
      PostgreSQL        Redis           Kafka
          │               │                │
          │               │        ┌───────┴────────┐
          │               │        │                │
          ▼               ▼        ▼                ▼
   Domain Logic     Cache/Rate  Event Proc     Alert Engine
```

## Layers
- **Controller Layer**: REST APIs & WebSocket entry points.
- **Application Service Layer**: High-level workflow orchestration.
- **Domain Layer**: Core logic, validations, state machine.
- **Infrastructure Layer**: Database Repositories, Kafka Producers/Consumers, Redis adapters.
