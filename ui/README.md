# Keycloak Operations Fleet Console — UI

React + TypeScript + Vite frontend for the **Keycloak Operations Platform** (milestone 0.7).

## Requirements

- **Node.js ≥ 20** (see `engines.node` in `package.json`)
- **npm** (comes with Node)
- A running backend at `http://localhost:8081` (see main project `dev/` setup)

## Quick Start (with backend)

```bash
# 1. Start the backend (from repo root)
podman compose -f dev/compose.yaml up -d
mvn quarkus:dev   # or: java -jar target/quarkus-app/quarkus-run.jar

# 2. Start the UI dev server
cd ui
npm install
npm run dev
# Open http://localhost:3000
```

## Environment Variables

Copy `.env.example` to `.env.local` and adjust:

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8081` | Backend base URL (no trailing slash) |
| `VITE_AUTH_MODE` | `OPEN_LAB` | `OPEN_LAB` (no auth) or `OIDC` |
| `VITE_OIDC_AUTHORITY` | — | OIDC issuer URL (OIDC mode only) |
| `VITE_OIDC_CLIENT_ID` | — | OIDC client ID (OIDC mode only) |

**Never put secrets in `.env` files committed to the repository.**

## Auth Modes

- **OPEN_LAB** (default): No authentication required. The backend must also be in OPEN_LAB mode. Suitable for local development.
- **OIDC**: Set `VITE_AUTH_MODE=OIDC` and configure OIDC variables. The frontend reads the auth mode from `GET /api/v1/me` and will handle token exchange in a future release.

## Vite Proxy

In dev mode, the Vite server proxies `/api` → `http://localhost:8081`, so you can configure `VITE_API_BASE_URL` as empty or the UI origin to avoid CORS issues.

## Scripts

| Script | Description |
|---|---|
| `npm run dev` | Start dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview production build locally |
| `npm test` | Run tests in watch mode |
| `npm run test:run` | Run tests once (CI) |
| `npm run lint` | Type-check only (`tsc --noEmit`) |

## Production Build (nginx)

```bash
# Local static build
npm run build
# Serve ./dist/ with nginx; ensure nginx rewrites unknown paths to index.html

# Or build the production image with Podman
podman build -t keycloak-operations-ui -f Containerfile .
```

Example nginx snippet:

```nginx
server {
  root /usr/share/nginx/html;
  location / {
    try_files $uri $uri/ /index.html;
  }
  # Proxy API requests to the backend
  location /api/ {
    proxy_pass http://keycloak-ops-backend:8081/api/;
  }
}
```

## Architecture

```
Browser
  └─ React SPA (this module)
       └─ /api/v1 REST + SSE
            └─ Operations Backend (Quarkus)
                 ├─ Keycloak / RHBK (Admin REST — read-only)
                 ├─ OpenShift / Kubernetes (inventory)
                 ├─ Prometheus (metrics)
                 └─ PostgreSQL (history)
```

The browser **never** calls Keycloak Admin, Kubernetes/OpenShift APIs, Prometheus, or PostgreSQL directly.

## Pages

| Route | Page |
|---|---|
| `/` → `/targets` | Fleet dashboard |
| `/targets/:targetId` | Target overview |
| `/targets/:targetId/health` | Health checks |
| `/targets/:targetId/assessment` | Assessment + findings |
| `/targets/:targetId/performance` | Semantic metrics |
| `/targets/:targetId/infrastructure` | Snapshots / inventory |
| `/targets/:targetId/history` | History (assessments, health, snapshots, audit) |

## Testing

```bash
npm run test:run
```

Uses **Vitest** + **Testing Library** + **jsdom**. Tests cover:
- Fleet rendering (loading, error, empty, data)
- Target navigation
- Status badges (healthy/unhealthy)
- Assessment + findings rendering
- Metrics unavailable/stale (MetricValue never shows missing as 0)
- API error states
- Partial assessment handling
- Empty history
- Target isolation (switching targets clears stale data)
