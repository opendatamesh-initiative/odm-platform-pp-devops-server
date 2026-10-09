# Development setup

Local developer setup for the DevOps Server.

Related: [Configuration](configuration.md) · [Deployment](deployment.md)

## Prerequisites

- **Java 21** (see `pom.xml` `java.version`)
- **Maven** 3.6 or higher (or the included `./mvnw` wrapper)
- **PostgreSQL** 12+ (for `localpostgres` / `docker`) or **H2** (in-memory, used by the `dev` profile)
- **Docker** (optional to run the service; **required** for Testcontainers during `mvn verify`)

## Clone and build

```bash
git clone https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server.git
cd odm-platform-pp-devops-server
mvn clean install
```

## Running locally

Root `application.yml` may default `spring.profiles.active` to **`test`**. For a normal local run, override with `dev` or `localpostgres`.

```bash
# H2 in-memory; port 8002 (application-dev.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Local PostgreSQL; port 8002
mvn spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

Run the packaged JAR the same way:

```bash
mvn clean package
java -Dspring.profiles.active=dev -jar target/odm-platform-pp-devops-server-*.jar
```

### Profiles useful for local work

| Profile / file | Purpose |
|----------------|---------|
| `application-dev.yml` | H2 in-memory DB; server port **8002**; sample Notification address and a full-control executor |
| `application-localpostgres.yml` | Local PostgreSQL datasource; port **8002** |
| `application-docker.yml` | PostgreSQL via `DB_*` env vars; default profile of the container image |
| `application-test.yml` (test resources) | Integration tests; schema `odm_devops_test` |

Datasource, schema, Notification, Policy, and executor properties: [Configuration](configuration.md).

### API docs (local)

With the `dev` profile:

| | Endpoint |
|:--|:---------|
| Swagger UI | http://localhost:8002/swagger-ui.html |
| OpenAPI | http://localhost:8002/v3/api-docs |

Product-plane prefix: **`/api/v2/pp/devops/`**.

### External services for local execution flows

The approval loop needs the Notification service when `odm.product-plane.notification-service.active=true`. Policy is optional — see [Policy service](../service/policy-service.md). Full-control tasks also need the executor named in configuration to be reachable.

## Testing

```bash
mvn -B verify -Dspring.profiles.active=test
```

Docker must be available for Testcontainers when integration tests run.

## Stack

- **Spring Boot 3.5.x**
- **PostgreSQL** + Flyway (`src/main/resources/db/migration/postgresql/`), schema `odm_devops`
- **H2** for `dev`
- **Spring Data JPA**, **SpringDoc OpenAPI**, **MapStruct**
- Optional **Notification** client

## Next steps

- Configuration (DB, Notification, Policy, executors): [Configuration](configuration.md)
- Container run: [Deployment](deployment.md)
- Service concepts: [docs index](../README.md)

---

↑ Back to [docs index](../README.md)
