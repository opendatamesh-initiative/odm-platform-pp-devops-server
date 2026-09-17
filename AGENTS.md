# ODM Platform service template — agent guide

## Scope and priorities

- These instructions apply to the entire repository (a GitHub **template** for ODM Platform
  Spring Boot services). After a fork, update names, packages, API prefixes, and this file.
- Follow the user's current request first. Treat an approved SPDD prompt as the implementation
  contract, and `spdd/norms/` as the canonical source for engineering conventions.
- Preserve unrelated working-tree changes. Keep edits focused; do not retrofit untouched code
  merely to satisfy current norms.

## Project snapshot

- Spring Boot **3.5** / Java **21** Maven service (`./mvnw` or `mvn`). PostgreSQL + Flyway in
  real profiles; H2 for `dev`; Testcontainers Postgres for `test`.
- Product-plane REST under `/api/v2/pp/service-template/`. After a fork, that prefix and the
  Java base package `org.opendatamesh.platform.service.template` become
  `org.opendatamesh.platform.pp.<service>` and `/api/v2/pp/<service>/`.
- Shared infrastructure lives in `.../utils/` (generic CRUD, `UseCase` ports, RestUtils). Domain
  code is per-aggregate (sample: `example/`). Do not copy this template's `Example` aggregate
  into a real service as-is; replace it.
- Optional Notification HTTP client (`client/notification/`). Dummy bean when
  `odm.product-plane.notification-service.active` is `false`. No startup subscription and no
  observer controller unless you add them.
- Human docs: `README.md` (template onboarding) and `docs/setup/`. Feature specs:
  `spdd/analysis/`, `spdd/prompt/`.

## Required SPDD workflow

Use SPDD for product implementation work (features, fixes, and refactors):

1. `/spdd-analysis` from the business requirement.
2. `/spdd-reasons-canvas` from the resulting analysis (must read norms first — see below).
3. Obtain user confirmation of the structured prompt before implementation.
4. `/spdd-generate` and execute its Operations in order.
5. If implementation reality differs, update the prompt first, then adjust code. Use
   `/spdd-sync` or `/spdd-prompt-update` when the prompt must stay the source of truth.

Complete command contracts are in `.cursor/commands/spdd-*.md`. Cursor agents should invoke
those commands. Agents without slash-command support must read the corresponding command file
and perform the same stages and guardrails.

## Mandatory norms read gate

Before producing a REASONS Canvas, planning implementation details, changing source, or
reviewing source changes:

1. Read `spdd/norms/README.md` completely (the **norms registry**).
2. Derive applicable files from its **Norm index** and read those files completely with the
   Read tool. Do not hardcode a filename list — new registry entries must be picked up.
3. If applicability is unclear, read **every** registry-listed norm.

Do not rely only on the summary below. The norm files are authoritative. SPDD prompts must
**link** `spdd/norms/...` paths rather than copy long explanations.

High-risk defaults for new and touched code:

- **CRUD:** extend the shallowest `GenericCrud*` layer that fits; implement `validate`,
  `reconcile`, mapping (`toRes` / `toEntity`), and `getSpecFromFilters` as required. Prefer
  `*Resource` methods on HTTP adapters. Details: `spdd/norms/GENERIC-CRUD-GUIDELINES.md`.
  The sample `Example` aggregate is the in-repo pattern.
- **Use cases:** HTTP → `*UseCaseController` → `*UseCasesService` → `*Factory` → `UseCase` →
  outbound ports. The use-case package must not import REST `*Res` types. Commands and
  presenters are domain-only. Port impls are plain Java (`new` from the factory); the factory
  is the only `@Component` in that slice. Use cases must not call core CRUD services directly.
  Details: `spdd/norms/USE_CASE_IMPLEMENTATION.md`.
- **Composed method:** each method is a short outline at one abstraction level; business policy
  stays in the use case, mechanics in adapters.
- Throw `ServiceTemplateApiException` subclasses (`BadRequestException`, `NotFoundException`,
  `ResourceConflictException`, …) so `ResponseExceptionHandler` maps status codes. After a
  fork, rename that exception hierarchy together.

## Architecture map

Base package: `org.opendatamesh.platform.service.template`.

| Area | Role |
|------|------|
| `<aggregate>/entities`, `repositories`, `services/core` | JPA + generic CRUD |
| `<aggregate>/services/usecases/<name>` | Hexagonal use case (when behaviour is more than CRUD) |
| `rest/v2/controllers`, `rest/v2/resources` | HTTP adapters and DTOs / MapStruct mappers |
| `client/` | Outbound clients (interfaces here are mocked in tests) |
| `utils/` | Shared CRUD, use-case, RestUtils — change only when the shared pattern must change |
| `configuration/`, `exceptions/`, `rest/ResponseExceptionHandler` | Cross-cutting infra |
| `src/main/resources/db/migration/postgresql/` | Flyway; schema from `hibernate.default_schema` (lowercase only) |
| `src/test/java/.../rest/v2/` | `OdmPlatformServiceApplicationIT`, `TestContainerConfig`, `TestConfig`, `RoutesV2` |

`TestConfig` registers Mockito mocks for every **interface** under `.../client/**`. Keep outbound
clients as interfaces in that tree. Update the classpath pattern after a package rename.

## Setup and commands

Run from the repository root. Docker is required for `mvn verify` (Testcontainers).

```bash
# Install + unit tests + failsafe ITs
./mvnw -B verify -Dspring.profiles.active=test

# Local run (H2, port 8087)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Local PostgreSQL profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

`dev` / `localpostgres` listen on **8087**. Docker profile binds
`DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` (not `SPRING_DATASOURCE_*`). See
`docs/setup/development.md` and `docs/setup/configuration.md`.

Validation:

- Prefer a focused `*Test` (Surefire) or `*IT` (Failsafe) for the changed behaviour.
- Run `./mvnw -B verify -Dspring.profiles.active=test` for persistence, REST, Flyway, or
  cross-cutting changes.
- There is no Checkstyle/Spotless/JaCoCo plugin in this template; do not invent those commands.
- Report checks not run and why.

## Working agreements

- Follow neighbouring code only when it does not conflict with current norms; the sample
  `Example` CRUD is a precedent for anemic CRUD, not for skipping use-case boundaries on
  non-trivial workflows.
- Do not add Lombok, Spring Security, JGit, or extra GitHub Packages repositories unless the
  request needs them.
- Do not edit `target/` or commit secrets, `.env` files, or live credentials in YAML.
- Do not commit, tag, push, or publish images unless the user explicitly asks.
- Flyway `V1` in this template is sample schema (`examples`). After a fork, replace it with
  real migrations rather than piling unrelated tables onto the sample.
- When renaming the service, also update `AGENTS.md`, `spdd/norms/README.md`, `docs/`,
  `Dockerfile` JAR glob, and `.github/workflows/` `IMAGE_NAME`.
