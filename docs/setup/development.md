# Development

Local build, run, profiles, and testing.

Related: [Configuration](configuration.md) · [Deployment](deployment.md)

## Requirements

- Java **21** or higher
- Maven **3.6+** (or the included `./mvnw` wrapper)
- PostgreSQL 12+ (for `localpostgres` / `docker`) or H2 (for `dev`)
- Docker (optional for run; **required** for Testcontainers during `mvn verify`)

## Profiles

| Profile | Role |
|:--------|:-----|
| **`dev`** | H2 in-memory DB; server port **8087** (`application-dev.yml`) |
| **`docker`** | PostgreSQL via env vars (`application-docker.yml`) |
| **`localpostgres`** | Local PostgreSQL example; port **8087** |
| **`test`** | Integration tests (`src/test/resources/application-test.yml`) |

Root `application.yml` may default `spring.profiles.active` to **`test`**. For a normal local run, override with `dev` or `localpostgres`.

## Run with Maven

```bash
mvn clean install

# H2
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Local PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

## Run with the JAR

```bash
mvn clean package
java -Dspring.profiles.active=dev -jar target/odm-platform-service-template-*.jar
```

## API docs (local)

| | Endpoint |
|:--|:---------|
| Swagger UI | http://localhost:8087/swagger-ui.html |
| OpenAPI | http://localhost:8087/v3/api-docs |

Product-plane prefix: **`/api/v2/pp/service-template/`**. After renaming the service, update this prefix in controllers and `RoutesV2`.

## Testing

```bash
mvn -B verify -Dspring.profiles.active=test
```

Docker must be available for Testcontainers when integration tests run.

## Architecture (stack)

- **Spring Boot 3.5.x**
- **PostgreSQL** + Flyway (`src/main/resources/db/migration/postgresql/`), schema `odm_service_template`
- **H2** for `dev`
- **Spring Data JPA**, **SpringDoc OpenAPI**, **MapStruct**
- Optional **Notification** client (`NotificationClientConfig`)

---

↑ Back to [docs index](../README.md)
