# ServiceDNA

ServiceDNA is a service health, dependency, incident, and observability platform. 
It allows teams to register backend services and monitor their health, availability, latency, failures, dependencies, and incidents from a central dashboard.

## Features
- **Service Registry**: Register backend services.
- **Health Check Engine**: Scheduled HTTP health checks.
- **Dependency Graph**: Model and visualize dependencies between services.
- **Event-Driven**: Asynchronous architecture powered by Kafka.
- **Incident Management**: Automatically generate incidents on repeated failures.
- **Real-time Monitoring**: WebSocket updates for the live dashboard.
- **Alerting & Notifications**: Configurable alerts and routing.

## Tech Stack
- **Java 21**
- **Spring Boot 3.2**
- **PostgreSQL**
- **Redis**
- **Apache Kafka**

## Getting Started
To run the local infrastructure (Postgres, Redis, Kafka):
\`\`\`bash
docker-compose up -d
\`\`\`

To start the backend:
\`\`\`bash
cd backend
./mvnw spring-boot:run
\`\`\`
