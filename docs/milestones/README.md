# Milestones

Product delivery slices. Status from Git / `pom.xml` / code — not chat history.

| Milestone | Description | Status |
|-----------|-------------|--------|
| [0.1](0.1-keycloak-admin-readonly.md) | Keycloak Admin Read-only | COMPLETED |
| [0.2](0.2-multi-target.md) | Multi-target | COMPLETED |
| [0.3](0.3-platform-foundation.md) | Platform Foundation | COMPLETED |
| [0.4](0.4-infrastructure-discovery.md) | Infrastructure Discovery | COMPLETED |
| [0.5](0.5-health-assessment.md) | Health & Assessment | COMPLETED |
| [0.6](0.6-prometheus-metrics.md) | Prometheus Metrics | COMPLETED |
| [0.6.1](0.6.1-metrics-hardening.md) | Metrics Hardening | COMPLETED |
| [0.7](0.7-web-ui.md) | Web UI | COMPLETED |
| [0.8](0.8-controlled-administration.md) | Controlled Administration & Change Management | COMPLETED |
| [0.8.0 hardening](0.8.0-hardening-backlog.md) | Close 0.8 spec gaps (expire, RBAC, policies, IT) | **IN PROGRESS** |
| 0.8.1 | Realm & Client Administration | PLANNED *(after 0.8.0 hardening)* |
| 0.8.2 | Users, Groups & Roles | PLANNED |
| 0.8.3 | Authentication Flows & Client Scopes | PLANNED |
| 0.8.4 | Identity Providers & Advanced Realm Configuration | PLANNED |
| 0.9 | Schedules / alerts | PLANNED |
| 1.0 | Production-ready platform | PLANNED |

Milestones list **requirement IDs**, scope, and acceptance — not Agent prompts.  
Workflow: [`../../AGENTS.md`](../../AGENTS.md) · HEAD snapshot: [`../project-state.md`](../project-state.md)

## Git mapping (selected)

| Commit | Notes |
|--------|--------|
| `7270d34` | Conceptual 0.1–0.3 (`0.1.0`) |
| `64a0f8c` | 0.4 |
| `a5a8d08` | Track `target` Java package |
| `a0ffe9b` | 0.5 |
| `59461d1` | 0.6 |
| `089f6ea` | 0.6.1 hardening |
| `bcad150` | 0.7 Web UI |
| *(HEAD)* | 0.8 Controlled Administration — [hardening backlog](0.8.0-hardening-backlog.md) |
