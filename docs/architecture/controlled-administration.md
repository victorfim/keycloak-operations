# Controlled administration & change management

How the platform performs **safe, semantic, target-bound** writes against Keycloak/RHBK.

Related: [ADR 0007](../adr/0007-plan-approve-apply-change-model.md),
[milestone 0.8](../milestones/0.8-controlled-administration.md),
[security](security.md), [persistence](persistence.md).

## Principle

AI and REST callers never receive unrestricted write access.

```text
User / AI
    │
    ▼
Semantic Change Request   (targetId + resource + desired state)
    │
    ▼
Change Planning           (read current → diff → risk → policy)
    │
    ▼
Change Plan               (fingerprint / baseline hash)
    │
    ▼
Explicit Approval         (bound to plan fingerprint)
    │
    ▼
Apply                     (Admin REST via adapter only)
    │
    ▼
Read-back Verification
    │
    ▼
Audit
```

The LLM may request and explain changes. It **MUST NOT** decide authorization,
risk, approval requirement, policy outcome, or verification result.

## Never expose

- Raw Keycloak Admin REST paths or methods
- Arbitrary JSON mutation tools
- Credentials, passwords, client secrets, tokens, private keys

## Domain model

| Concept | Role |
|---------|------|
| ChangeRequest | Desired semantic mutation (not raw HTTP) |
| ChangePlan | Planned ops + safe diff + risk + policy + fingerprints |
| ChangeOperation | Single property-level mutation within a plan |
| ChangeDiff | ADDED / REMOVED / CHANGED / UNCHANGED (secrets redacted) |
| ChangeRisk | LOW / MEDIUM / HIGH / CRITICAL (deterministic) |
| ChangePolicyDecision | ALLOW / APPROVAL_REQUIRED / DENY |
| ChangeApproval | Approver + timestamp bound to plan fingerprint |
| ChangeExecution | Apply attempt metadata |
| ChangeVerification | Read-back comparison result |
| ChangeResult | Terminal outcome for callers |

Statuses (typical): `PLANNED` → `WAITING_APPROVAL` → `APPROVED` → `APPLYING` →
`APPLIED` / `VERIFIED` / `FAILED` / `REJECTED` / `EXPIRED`.

TTL enforcement (`change.expiration.after`, default 7d) expires `WAITING_APPROVAL`,
`PLANNED`, and `APPROVED` records via expire-on-read, a scheduled job, or
`POST /api/v1/changes/{id}/expire`. Mutations on expired records return `CHANGE_EXPIRED`.

## Authorization

Target-scoped permissions:

| Permission | Meaning |
|------------|---------|
| READ | Read target configuration |
| ASSESS | Run assessments / health |
| PLAN | Create plans and diffs |
| WRITE | Apply approved non-admin changes |
| ADMIN | High-impact administrative applies (future) |

Global `mcp.read-only=true` (default) denies WRITE/ADMIN Keycloak mutations.
PLAN remains available so operators can dry-run.

## Environment policy

Defaults (configurable):

| Env | WRITE | Notes |
|-----|-------|-------|
| DEV | ALLOW or APPROVAL_REQUIRED by risk | LOW may skip explicit approval |
| TEST / HML / STAGING | APPROVAL_REQUIRED | |
| PRD | APPROVAL_REQUIRED | Always for production writes |
| Any | DELETE | DENY in 0.8 foundation |

## Integrity & concurrency

- **Plan fingerprint** — deterministic hash of planned operations.
- **Baseline fingerprint** — hash of observed current state at plan time.
- Approval stores the approved fingerprint; apply refuses mismatches (`APPROVAL_INVALID`).
- Before apply, re-read resource; baseline drift → `CHANGE_CONFLICT` / `REPLAN_REQUIRED`.

## Verification

After Admin API mutation success:

1. Read resource again
2. Compare to desired state
3. Persist `VERIFIED` or `VERIFICATION_FAILED`

HTTP 2xx alone is insufficient.

## Persistence

Flyway migration `V7` introduces `change_records` (aggregate lifecycle + JSON plan/diff/verification).
Secrets are never stored; values pass `SensitiveDataFilter` before persist.

## Surfaces

| Surface | Role |
|---------|------|
| Application service | `ChangeManagementService` — sole business logic |
| MCP | `keycloak_get/list/approve/reject/apply/verify_change` + semantic plan tools |
| REST | `/api/v1/changes...` |
| Web UI | Minimal pending/detail/history views (not a full admin console) |

## Proof-of-concept mutation (0.8)

Controlled non-sensitive **client configuration update** (allowlisted properties such as
display name / description / PKCE challenge method). Demonstrates the full lifecycle
without delete, password, or secret workflows.

Broader realm/client/user/flow/IdP administration belongs to milestones **0.8.1–0.8.4**.
