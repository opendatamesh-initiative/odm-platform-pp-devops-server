# ODM Platform DevOps Server

Spring Boot **3.5** / Java **21** Maven service. Package `org.opendatamesh.platform.pp.devops`.
REST `/api/v2/pp/devops/`. User request wins over this file.

Human setup: `README.md`, `docs/setup/`. Architecture map: `ARCHITECTURE.md`.
Feature specs: `spdd/analysis/`, `spdd/prompt/`.
Engineering conventions: `spdd/norms/` (authoritative; do not copy them here).

## Commands

Run from the repository root. Docker is required for `verify` (Testcontainers).

```bash
./mvnw -B verify -Dspring.profiles.active=test                          # unit + failsafe ITs
./mvnw -B -Dtest=ClassName test                                        # one Surefire class
./mvnw -B -Dit.test=ClassNameIT verify -Dspring.profiles.active=test    # one Failsafe IT
./mvnw spring-boot:run -Dspring-boot.run.profiles=localpostgres        # local Postgres, 8002
```

- Prefer the focused `*Test` / `*IT` for the change; use full `verify` for persistence, REST, Flyway, or cross-cutting work.
- There is no Checkstyle, Spotless, or JaCoCo plugin. Do not invent those commands. Report checks not run.
- Docker profile binds `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` (not `SPRING_DATASOURCE_*`).

## Where behavior goes

Two stacks. Pick one before editing. Detail and package map: `ARCHITECTURE.md`. Procedures: the matching norm, not this file.

| Intent | Stack | Norm |
|--------|--------|------|
| Admin or import endpoint; little or no extra domain policy | Anemic CRUD: `*Controller` → `services/core` `Generic*Crud*` → repository | `spdd/norms/GENERIC-CRUD-GUIDELINES.md` |
| User story: business rules, domain checks, side effects | Use case: `*UseCaseController` → `*UseCasesService` → factory → `UseCase` → outbound ports | `spdd/norms/USE_CASE_IMPLEMENTATION.md` |

- Today only Activity anemic CRUD exists. The use-case stack is the intended shape (same as `odm-platform-pp-registry-server`) and is not in this tree yet.
- A use case calls core CRUD through an outbound port, not from the use case class.
- `utils/` is shared infrastructure. Change it only when the shared pattern must change.

## SPDD and norms

For product work (features, fixes, refactors):

1. `/spdd-analysis` → user clarifies Requirement Ambiguities (if any) → `/spdd-reasons-canvas` (read norms first) → user confirms the prompt.
2. `/spdd-generate` and execute Operations in order.
3. If code diverges, update the prompt first (`/spdd-sync` or `/spdd-prompt-update`), then the code.

Command contracts: `.cursor/commands/spdd-*.md`. Invoke the slash command when available; otherwise read the file and follow the same stages.

Before planning, changing, or reviewing source:

1. Read `spdd/norms/README.md` (the registry).
2. Read every **Norm index** file that applies. Do not hardcode the filename list.
3. If applicability is unclear, read every registry-listed norm.

SPDD prompts must **link** `spdd/norms/...` rather than paste long explanations.

## Boundaries

- **Always:** keep edits in scope; preserve unrelated working-tree changes; do not retrofit untouched code merely to match current norms. After changes, review `docs/` and update it so setup, config, APIs, and behavior stay aligned.
- **Ask first:** commit, tag, push, publish images; add Maven dependencies or GitHub Packages repositories.
- **Never:** commit secrets, `.env`, or live credentials in YAML; add Lombok, Spring Security, or JGit unless the request needs them; add later domain tables to Flyway `V1` (V1 is the Activity aggregate schema — new tables go in a new migration).
