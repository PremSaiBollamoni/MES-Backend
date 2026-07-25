<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=32&duration=3000&pause=1000&color=0E7C86&center=true&vCenter=true&width=600&lines=PALMS+Backend+%E2%80%93+MES+Engine;Spring+Boot+%2B+MySQL+%2B+Claude+AI;Production-grade+Factory+API" alt="Typing SVG" />

<br/>

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=for-the-badge&logo=flyway&logoColor=white)](https://flywaydb.org)
[![Claude](https://img.shields.io/badge/Claude-AI%20Reports-D97757?style=for-the-badge&logo=anthropic&logoColor=white)](https://www.anthropic.com)

**The API and business logic behind PALMS — a real-time Manufacturing Execution System for garment factories.**

</div>

---

## What is this?

This is the **backend** for **PALMS** (Parallel Assembly Line Management System): a Spring Boot service that powers production tracking, workforce management, stock control, and AI-driven analytics for a garment manufacturing floor — serving the [Flutter frontend](https://github.com/PremSaiBollamoni/MES-Frontend) across Android, Windows, and Web.

Built as a real internal tool, not a tutorial project: role-based authorization on every endpoint, schema evolution entirely via versioned Flyway migrations, and an AI reporting pipeline that is architecturally forbidden from fabricating data.

## Highlights

- 🔐 **Custom JWT authentication + activity-based authorization** — every endpoint is gated by a named "activity" tied to the caller's role, enforced by a request interceptor, not client-side trust.
- 🏭 **Full production domain model** — stations, SAM/target-based station design, operator allocation, multi-operator team tracking, attendance sync from an external HRMS, WIP/bundle tracking, and 4-tier stock availability.
- 🤖 **Claude-powered report generation** — aggregates real production/stock/attendance/performance metrics into a single source-of-truth JSON, hands it to Claude (Haiku 4.5) with an explicit "never invent a number, omit what isn't tracked" contract, and renders the result server-side to a downloadable PDF (jsoup-sanitized HTML → Flying Saucer/OpenPDF).
- 🗄️ **Flyway-owned schema** — 24+ sequential migrations, Hibernate locked to `validate` so the schema can never silently drift from what's reviewed and versioned.
- 🔒 **Field-level encryption** — sensitive employee fields (address, emergency contact) are AES-256-GCM encrypted at the application layer, not just at rest.
- 🌐 **External HRMS integration** — pulls biometric attendance data from a real factory HRMS portal on a scheduled sync.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.1 (Web, Data JPA) |
| Database | MySQL 8 |
| Migrations | Flyway |
| Auth | JWT (`jjwt`), custom `RoleAuthorizationInterceptor` |
| AI | Anthropic Java SDK (Claude Haiku 4.5) |
| PDF Rendering | Flying Saucer (OpenPDF) + jsoup HTML sanitization |
| Build | Maven |

## Architecture at a Glance

```
Controller  →  @RequiresActivity("X")-gated REST endpoints
Service     →  business logic, one per domain (Station, Attendance, Report, ...)
Repository  →  Spring Data JPA
Model       →  JPA entities
db/migration → versioned Flyway SQL, the single source of schema truth
report/      → data aggregation + Claude prompt orchestration + PDF rendering
```

## Getting Started

```bash
# 1. Configure environment (see application.properties for the full list)
export DB_URL="jdbc:mysql://localhost:3306/MES"
export DB_USERNAME="mes_app_user"
export DB_PASSWORD="..."
export ANTHROPIC_API_KEY="..."   # optional — enables AI report generation

# 2. Run
./mvnw spring-boot:run
```

Schema is created automatically on first run via Flyway. No manual SQL required.

## Deployment

Includes a multi-stage `Dockerfile` (Maven build → slim JRE runtime) — deploys cleanly to any container platform (Railway, Render, Fly.io, etc.) with the same environment variables as local dev. `server.port` respects the `PORT` env var for PaaS compatibility.

## Related

- **Frontend** — [MES-Frontend](https://github.com/PremSaiBollamoni/MES-Frontend) (Flutter, Android/Windows/Web)

---

<div align="center">

Built by **Prem Sai** — engineered for a real, running factory floor.

</div>
