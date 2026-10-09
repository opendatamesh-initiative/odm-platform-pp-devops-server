# Deployment

How to deploy the DevOps Server as a container (recommended for production and shared environments).

Related: [Configuration](configuration.md) · [Development](development.md) · [Activity lifecycle](../service/activity-lifecycle.md) · [Events](../service/events.md) · [Policy service](../service/policy-service.md)

## What you need

| Dependency | Required? | Notes |
|------------|-----------|-------|
| **PostgreSQL** | Yes | Production database. Set URL, credentials, and schema — see [Configuration](configuration.md#database) |
| **Notification service** | Yes for event-driven execution | Approval loop. Set `active` + `address`, and set `server.baseUrl` so Notification can call the observer back |
| **Policy service** | Optional | When inactive, DevOps auto-approves `*_REQUESTED`. DevOps does not call a Policy URL |
| **Executors** | Yes for any task you run | Declare each executor as `full-control` (address required) or `instrumented` (address optional) |

The container image ships the application only. You provide the database, Notification, and executor URLs via configuration.

## Get the image

Prefer a **versioned release image**. On each GitHub release, the CI/CD pipeline builds and publishes the image.

| Source | Image | Notes |
|--------|-------|--------|
| **Docker Hub** | [`opendatamesh/odm-platform-devops`](https://hub.docker.com/r/opendatamesh/odm-platform-devops) | Tags match release versions. |
| **Build locally** | Your own tag | Use when you need unreleased changes or a custom build. |

### Pull from Docker Hub

```bash
docker pull opendatamesh/odm-platform-devops:<tag>
```

### Build from source

The image expects a Maven-built JAR under `target/` (the Dockerfile copies `odm-platform-pp-devops-server-*.jar`).

```bash
mvn -B package -DskipTests
docker build -t odm-platform-pp-devops-server .
```

## Configure and run

The image defaults to the **`docker`** Spring profile (`PROFILES_ACTIVE=docker`), which binds the datasource from:

| Environment variable | Maps to |
|----------------------|---------|
| `DB_JDBC_URL` | `spring.datasource.url` |
| `DB_USERNAME` | `spring.datasource.username` |
| `DB_PASSWORD` | `spring.datasource.password` |

For a full production-like setup (schema, `server.baseUrl`, Notification, Policy, executors), pass nested Spring config as **`SPRING_PROPS`**. The image forwards that value as extra JVM arguments, so include `-Dspring.application.json=`. Property meanings: [Configuration](configuration.md#minimal-production-like-configuration).

### Minimal run (datasource only)

Useful to smoke-test connectivity. Add Notification, `server.baseUrl`, and executors before relying on execution flows.

```bash
docker run --name odm-devops -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_devops \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  opendatamesh/odm-platform-devops:<tag>
```

### Production-like run (`SPRING_PROPS`)

```bash
docker run --name odm-devops -p 8080:8080 \
  -e SPRING_PROPS='-Dspring.application.json={"server":{"baseUrl":"https://devops.example.com"},"spring":{"datasource":{"url":"jdbc:postgresql://db:5432/odm_devops","username":"your_username","password":"your_password"},"jpa":{"properties":{"hibernate":{"default_schema":"odm_devops"}}}},"odm":{"product-plane":{"notification-service":{"address":"http://notification-service:8001","active":true},"policy-service":{"active":false}},"utility-plane":{"executor-services":{"starter":{"address":"http://executor:9080","execution-mode":"full-control"},"cli":{"execution-mode":"instrumented"}}}}}' \
  opendatamesh/odm-platform-devops:<tag>
```

Replace the image name with your locally built tag if you are not using Docker Hub.

Optional: `JAVA_OPTS` for JVM flags; `PROFILES_ACTIVE` to override the default `docker` profile.

## Deploy checklist

1. Provision **PostgreSQL** and choose a lowercase schema (default `odm_devops`). Flyway applies migrations on startup.
2. Decide **Policy** on or off. If on, the external Policy service publishes decisions on Notification; set `odm.product-plane.policy-service.active=true`.
3. Deploy **Notification**, point DevOps at it, and set **`server.baseUrl`** so Notification can reach `POST /api/v2/up/observer/notifications`.
4. Declare **executors** (`full-control` with an address, `instrumented` with or without one).
5. Pull or build the **image**, inject config (`DB_*` and/or `SPRING_PROPS`), and expose port **8080** (or your `server.port`).
6. Confirm the app is up: Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`.

## Further reading

- Properties to manage: [Configuration](configuration.md)
- Local build without containers: [Development](development.md)
- Execution and observer behavior: [Activity lifecycle](../service/activity-lifecycle.md), [Events](../service/events.md), [Policy service](../service/policy-service.md)
