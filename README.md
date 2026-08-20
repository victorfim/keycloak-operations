# Keycloak / RHBK Operations

Backend + Web UI for **administration**, **diagnostics**, **health**, **assessment**, and **semantic metrics** of [Keycloak](https://www.keycloak.org/) and [Red Hat build of Keycloak (RHBK)](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak) environments.

| | |
|---|---|
| Version | **0.7.0-SNAPSHOT** *(experimental / evolving)* |
| Artifact | `io.github.keycloakmcp:keycloak-operations-mcp` |
| Web UI | `ui/` (React + TypeScript + Vite) |
| Runtime | Java **21**, Quarkus **3.38.1**, Node **≥ 20** (UI) |
| License | [Apache License 2.0](LICENSE) |
| Repository | https://github.com/csfreitas/keycloak-operations |

## Why it exists

Operators ask natural-language and console questions about realms, HA posture, misconfigurations, and runtime health across **many** environments. This project provides a **structured, auditable** MCP + REST backend — with redaction, target isolation, and deterministic assessments — and a Fleet Operations Console that consumes only those APIs.

## Current capabilities

- Multi-target Keycloak/RHBK **read-only** Admin tools (MCP)
- REST `/api/v1` for fleet, history, inventory, assessments, health, metrics
- OpenShift/Kubernetes infrastructure inventory (target-aware)
- Deterministic assessment engine + health checks
- Semantic Prometheus / OpenShift Monitoring metrics (no raw PromQL from clients)
- PostgreSQL persistence (Flyway) for operational history — **not** a TSDB
- **Fleet Operations Console** (`ui/`) — fleet, overview, health, assessment, performance, infrastructure, history

Status detail: [`docs/project-state.md`](docs/project-state.md).

## Architecture (summary)

```text
Browser (ui/) → REST / SSE → Application Services → Target Registry
MCP agents  ↗                 → Keycloak / Infrastructure / Metrics providers
                              → Evidence → Rules → Findings
```

The browser never talks to Keycloak Admin, Kubernetes/OpenShift, Prometheus, or PostgreSQL directly.

See [`docs/architecture/`](docs/architecture/) and [`docs/ui-architecture.md`](docs/ui-architecture.md).

## Quick start

```bash
# Local Keycloak + PostgreSQL (+ Prometheus as documented)
podman compose -f dev/compose.yaml up -d

# Backend
mvn quarkus:dev
# MCP Streamable HTTP typically at http://localhost:8081/mcp
# REST at http://localhost:8081/api/v1

# Web UI (separate terminal)
cd ui && npm ci && npm run dev
# http://localhost:3000
```

Build / test:

```bash
mvn clean verify
cd ui && npm ci && npm run test:run && npm run build
```

More: [`docs/development.md`](docs/development.md), [`ui/README.md`](ui/README.md).

## Documentation

| Path | Purpose |
|------|---------|
| [docs/project-state.md](docs/project-state.md) | HEAD snapshot for humans & agents |
| [docs/requirements/](docs/requirements/) | **What** (normative requirements) |
| [docs/architecture/](docs/architecture/) | **How** |
| [docs/adr/](docs/adr/) | **Why** (decisions) |
| [docs/milestones/](docs/milestones/) | **When** (delivery slices) |
| [docs/roadmap.md](docs/roadmap.md) | Compact roadmap |
| [AGENTS.md](AGENTS.md) | AI coding bootstrap |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guide |
| [ui/README.md](ui/README.md) | Fleet Console frontend |

## Roadmap

0.1–0.7 ✅ → **0.8 drift (next)** → 0.9 schedules/alerts → 1.0.
Details: [`docs/roadmap.md`](docs/roadmap.md).

## Status

This repository is under active development (`*-SNAPSHOT`). APIs and tools may change. Compatibility claims distinguish **tested** vs **design-compatible** — see [`docs/compatibility.md`](docs/compatibility.md).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). AI-assisted contributions are welcome; contributors remain responsible for correctness, tests, and review.
