# Project state (HEAD)

Compact recovery context. **Git, code, tests, and docs are authoritative — not chat history.**

Derive current commit with `git rev-parse --short HEAD` (do not treat any SHA below as permanent HEAD).

## Documentation model

| Layer | Answers | Location |
|-------|---------|----------|
| Requirements | **what** | [`requirements/`](requirements/) |
| Architecture | **how** | [`architecture/`](architecture/) |
| ADR | **why** | [`adr/`](adr/) |
| Milestones | **when** | [`milestones/`](milestones/) |
| Tests | **verification** | `mvn clean verify` + `ui/` npm test/build |
| AGENTS.md | **AI workflow** | [`../AGENTS.md`](../AGENTS.md) |

## What is this project?

**Keycloak / RHBK Operations Platform** (`keycloak-operations-mcp` + `ui/`): MCP + REST `/api/v1`, multi-target Keycloak/RHBK admin, OpenShift/Kubernetes inventory, health & deterministic assessments, PostgreSQL history, semantic Prometheus metrics, Fleet Operations Console (Web UI), and **controlled administration** (plan → approve → apply → verify).

Repo: https://github.com/csfreitas/keycloak-operations

## Current Version

`0.8.0-SNAPSHOT` (`pom.xml` / `ui/package.json`)

Historical milestone commits (immutable):

| Milestone | Commit |
|-----------|--------|
| 0.7 Web UI | `bcad150` |
| 0.6.1 Metrics Hardening | `089f6ea` |
| 0.6 Metrics | `59461d1` |
| 0.5 Assessment depth | `a0ffe9b` |
| 0.4 Inventory | `64a0f8c` |

## Milestone status

| | |
|--|--|
| Latest completed | **0.8** Controlled Administration & Change Management |
| Hardening (IN PROGRESS) | [**0.8.0 backlog**](milestones/0.8.0-hardening-backlog.md) — close spec gaps before 0.8.1 |
| Next (PLANNED) | **0.8.1** Realm & Client Administration |
| Index | [milestones/README.md](milestones/README.md) |

## What already works

MCP + REST shared services; multi-target registry; Keycloak Admin reads + controlled writes via change lifecycle; Flyway V1–V7; inventory/discovery; assessment engine + profiles; HealthCheckEngine; semantic metrics; performance rules; lab compose; Fleet Operations Console with Changes views; optional OIDC profile (`%oidc`); OpenShift UI manifests.

**0.8 foundation:** ChangeRequest/ChangePlan lifecycle, safe diff, risk, environment policy, approval bound to plan fingerprint, apply with stale-plan protection, read-back verification, audit, semantic MCP/REST change tools, proof-of-concept non-sensitive client config update (`name` / `description` / `pkceCodeChallengeMethod`).

**0.8.0 hardening (HB-001):** change TTL expiration (`EXPIRED` / `CHANGE_EXPIRED`), expire-on-read, scheduled batch, manual expire API/MCP.

## Known limitations

- **0.8.0 hardening open** — see [backlog](milestones/0.8.0-hardening-backlog.md) (HB-001 **ACCEPTED**; HB-002–HB-005 P0 remain)
- Opt-in ITs skipped without `RUN_*_IT` + live stack (`ControlledClientChangeIT` placeholder)
- Full realm/client/user/flow/IdP administration deferred to 0.8.1–0.8.4
- Destructive ops, password/secret workflows out of scope
- SSE is in-process (not multi-replica fan-out)
- Identity A OIDC disabled by default (OPEN_LAB)
- VM inventory still future
- `mcp.read-only=true` by default; apply requires explicit opt-out + WRITE

## Test baseline (after HB-001)

```bash
mvn clean verify
cd ui && npm run test:run && npm run build
```

| | Result |
|--|--------|
| Backend unit | **161** run, 0 fail |
| Failsafe | **5** run, **5** skipped |
| Frontend | **50** Vitest tests |
| Builds | Backend + UI **SUCCESS** |

## New Agent Quick Start

1. Read [`AGENTS.md`](../AGENTS.md).
2. Read this file.
3. Read [`milestones/README.md`](milestones/README.md) → CURRENT/next milestone.
4. Read related requirements + architecture (for writes: [`architecture/controlled-administration.md`](architecture/controlled-administration.md)).
5. Run `mvn clean verify` (and `cd ui && npm test` when touching UI).
6. Inspect code before changing anything.
7. Implement only the agreed scope.
8. Do not commit/push without permission.
