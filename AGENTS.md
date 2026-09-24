# ODM Platform DevOps Server

Human setup: `README.md`, `docs/setup/`.
Feature specs: `spdd/analysis/`, `spdd/prompt/`.

## Docs

After a change, update `docs/` so a user can still understand the product and how to run it.

Write for a reader of the service, not for an implementer. Stay high-level: what the service does, how to set it up, and what to expect. Leave field names, SQL, HTTP contracts, class names, and package maps out of `docs/`. Those belong in code, OpenAPI, or `spdd/norms/`.

## Implementation

Style and implementation rules live in `spdd/norms/`. Read [`spdd/norms/README.md`](spdd/norms/README.md) before changing code, and follow the norm that applies. Do not copy those rules into this file.

| You are building… | Read |
|-------------------|------|
| Anemic CRUD | [`spdd/norms/GENERIC-CRUD-GUIDELINES.md`](spdd/norms/GENERIC-CRUD-GUIDELINES.md) |
| A use case | [`spdd/norms/USE_CASE_IMPLEMENTATION.md`](spdd/norms/USE_CASE_IMPLEMENTATION.md) |
