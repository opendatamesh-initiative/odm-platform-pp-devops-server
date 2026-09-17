# ODM Platform — service repository template

This repository is a **starting point** for new [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) microservices. Use it as a [GitHub template](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template) or clone it, then rename packages, configuration keys, and infrastructure identifiers to match your service.

The layout matches product-plane services such as the [Registry](https://github.com/opendatamesh-initiative/odm-platform-pp-registry-server) and [Blueprint](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server) servers.

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square" alt="License: Apache 2.0"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F.svg?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5"></a>
</p>

---

## What you get

- **Spring Boot 3.5**, **Java 21**, JPA, Flyway, Testcontainers, SpringDoc OpenAPI, Actuator, MapStruct.
- **Profiles:** `dev` (H2), `docker` (`DB_*` env vars), `localpostgres`, `test`.
- **Shared utils:** generic CRUD, hexagonal `UseCase` ports, REST error handling, HTTP client wrappers.
- **Notification client:** `NotificationClientConfig` + `NotificationClient` / `NotificationClientImpl` (HTTP when `odm.product-plane.notification-service.active` is `true`, in-process no-op when `false`). No startup event subscription and no observer REST endpoint — add those from the Registry when you need them.
- **Sample CRUD:** `Example` aggregate at `/api/v2/pp/service-template/examples` (replace when you add real domain).
- **SPDD:** Cursor commands under `.cursor/commands/` and norms under `spdd/norms/`. Agent onboarding: [`AGENTS.md`](AGENTS.md).
- **CI/CD:** GitHub Actions verify on `main`; release workflow publishes a Docker image.

This template does **not** implement policy integration, Git providers, or incoming observer endpoints.

## Quick start (local)

**Requirements:** Java **21** · Maven **3.6+** · Docker (for Testcontainers / `mvn verify`)

```bash
git clone <your-repo-url>
cd odm-platform-service-template

mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses an in-memory **H2** database (port **8087**). When the server is up:

| | Endpoint |
|:--|:---------|
| **Swagger UI** | [http://localhost:8087/swagger-ui.html](http://localhost:8087/swagger-ui.html) |
| **OpenAPI** | [http://localhost:8087/v3/api-docs](http://localhost:8087/v3/api-docs) |

API prefix: **`/api/v2/pp/service-template/`**.

More detail: [Development](docs/setup/development.md)

## Run with Docker

```bash
mvn clean package
docker build -t odm-platform-service-template .

docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_service_template \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-service-template
```

| Guide | Link |
|:------|:-----|
| Step-by-step deploy | [Deployment](docs/setup/deployment.md) |
| Properties to manage | [Configuration](docs/setup/configuration.md) |

---

## Documentation

All guides live under [`docs/`](docs/README.md).

| Guide | Description |
|:------|:------------|
| [Development](docs/setup/development.md) | Build, run, profiles, testing |
| [Deployment](docs/setup/deployment.md) | Containers and external dependencies |
| [Configuration](docs/setup/configuration.md) | DB, notification, observer identity |

---

## Customization checklist

Work through these in order. Prefer IDE **refactor → rename package** where possible; then fix YAML, SQL, Dockerfile, workflows, and hard-coded classpath patterns.

### 1. Maven coordinates (`pom.xml`)

| Location | Action |
|----------|--------|
| `groupId` | Your organisation (e.g. `org.opendatamesh`). |
| `artifactId` | Must match the JAR name glob in the `Dockerfile`. |
| `version` | Initial version; release tags should match for CD. |
| `name` / `description` / `url` / `<scm>` | Service branding and GitHub URLs. |

### 2. Java base package

Rename `org.opendatamesh.platform.service.template` under `src/main/java` and `src/test/java`, including `OdmPlatformServiceTemplateApplication` and `SpringBootTest` references. Product-plane convention is `org.opendatamesh.platform.pp.<service>`.

### 3. Configuration and database

- Adjust `application.yml` and profiles (ports, datasource, `odm.product-plane.notification-service`, `service-template` observer prefix).
- Align Flyway scripts and `default_schema` with your domain (replace the sample `examples` table).
- After renaming the YAML prefix, update `@Value("${service-template.observer...}")` in `NotificationClientConfig`.

### 4. Optional: API exception type

Rename `ServiceTemplateApiException` and subclasses, and update `ResponseExceptionHandler`.

### 5. Integration tests: client mocks

Update the Ant pattern in `TestConfig` from `classpath*:org/opendatamesh/platform/service/template/client/**/*.class` to your new package path.

### 6. Docker, GitHub Actions, and docs

- `Dockerfile` `COPY` glob vs `artifactId`.
- `.github/workflows/ci.yml` and `cicd.yml`: `IMAGE_NAME`, Docker Hub org, Maven `settings.xml` server ids.
- `spdd/norms/README.md`, `AGENTS.md`, and `docs/` titles/ports.

### 7. Replace this README

After forking, replace this document with a service-specific README (like Registry or Blueprint) describing your APIs and operational config.

---

## Summary of string locations

Search and replace template-specific tokens:

- `odm-platform-service-template` — POM `artifactId`, Spring app name, workflows, Docker, docs.
- `org.opendatamesh.platform.service.template` — packages, `TestConfig` classpath pattern.
- `OdmPlatformServiceTemplateApplication` — main class and tests.
- `service-template` / `odm_service_template` — YAML prefix and SQL schema.
- `/api/v2/pp/service-template/` — REST mapping and `RoutesV2`.
- `ServiceTemplateApiException` — if you rebrand exceptions.

After customization, run `mvn verify` and a local or container run with a real datasource before the first release.

---

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Part of the [Open Data Mesh Initiative](https://github.com/opendatamesh-initiative).
