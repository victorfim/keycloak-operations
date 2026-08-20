# Development

AI-assisted workflow: [`development/ai-assisted-development.md`](development/ai-assisted-development.md) and root [`AGENTS.md`](../AGENTS.md).

## Prerequisites

- Java 21+
- Maven 3.9+
- Node.js ≥ 20 + npm (Fleet Console in `ui/`)
- Podman (Compose)
- `curl` and `jq` (for setup / smoke scripts)

## Verified dependency versions (Maven Central, Aug 2026)

| Component | Version |
|-----------|---------|
| Quarkus | 3.38.1 |
| Quarkiverse MCP Server | 1.13.1 |
| Keycloak Admin Client | 26.0.12 |
| Keycloak container (local demo) | 26.7.1 |

## Local Keycloak

```bash
podman compose -f dev/compose.yaml up -d
./scripts/setup-dev.sh
```

This starts Keycloak Community `26.7.1`, imports realm `mcp-demo`, and creates the
`keycloak-mcp` service-account client in `master`.

**Warning:** `setup-dev.sh` grants broad admin roles for local convenience.
Production must use Fine-Grained Admin Permissions (FGAP) / least privilege —
**not** `realm-admin`.

## Build and unit tests

```bash
mvn clean verify
cd ui && npm ci && npm run test:run && npm run build
```

Unit tests do not require a running Keycloak. Integration / smoke checks do.

## Fleet Operations Console (local)

With backend on `:8081` and compose stack up:

```bash
cd ui
npm ci
npm run dev
# http://localhost:3000
```

Production UI image: `ui/Containerfile` (nginx unprivileged). Build with `podman build -t keycloak-operations-ui -f ui/Containerfile ui`. OpenShift: `deploy/openshift/100-ui-deployment.yaml` (+ service/route). See [`../ui/README.md`](../ui/README.md).

## Run (Streamable HTTP)

```bash
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_AUTH_REALM=master
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn quarkus:dev
```

MCP endpoint: `http://localhost:8081/mcp`  
Health: `http://localhost:9001/q/health` (MCP management; Keycloak health remains on `:9000`)

## Smoke test

```bash
./scripts/smoke-mcp.sh
```

## STDIO profile

```bash
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn -Pstdio quarkus:dev
```

See `.vscode/mcp.stdio.example.json` for a sample STDIO host configuration
(env var references; no secrets committed).

## Demo data

| Kind | Value |
|------|-------|
| Realm | `mcp-demo` |
| Users | `alice` / `alice`, `bob` / `bob` |
| Clients | `portal-web` (public), `backend-api` / `backend-api-secret` |
| Roles | `user`, `admin` |
| Groups | `users`, `administrators` |

Demo client secrets are for **local development only**.

## Project conventions

- Package root: `io.github.keycloakmcp`
- DTOs are Java records
- CDI `@ApplicationScoped` services
- No write MCP tools in 0.1.0
- Prefer feature flags over `version.equals` for capability detection
