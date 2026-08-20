# Architecture

How the Keycloak / RHBK Operations Platform is structured.

## Start here

0. [hld/README.md](hld/README.md) — high-level design (PT-BR / EN-US / ES-LA)
1. [overview.md](overview.md) — platform layers and flows
2. [multi-target.md](multi-target.md) — target registry and isolation
3. [assessment-engine.md](assessment-engine.md) — evidence → rules → findings
4. [security.md](security.md) — redaction, credentials, least privilege
5. [persistence.md](persistence.md) — PostgreSQL / Flyway (not a TSDB)
6. [observability.md](observability.md) — semantic metrics integration
7. [controlled-administration.md](controlled-administration.md) — plan / approve / apply / verify

## Related

- Requirements: [`../requirements/`](../requirements/)
- ADRs: [`../adr/`](../adr/)
- Milestones: [`../milestones/`](../milestones/)

Detailed catalogs (tools, rules, evidence keys, REST paths) remain as topic docs under `docs/` and are linked from the overview and milestones.
