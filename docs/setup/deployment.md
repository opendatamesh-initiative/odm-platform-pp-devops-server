# Deployment

Container deployment checklist.

Related: [Configuration](configuration.md) · [Development](development.md)

## Build image

```bash
mvn clean package
docker build -t odm-platform-pp-devops-server .
```

The `Dockerfile` copies `target/odm-platform-pp-devops-server-*.jar`.

## Run container

```bash
docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_devops \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-pp-devops-server
```

The image defaults to the **`docker`** Spring profile (`PROFILES_ACTIVE=docker`), which binds the datasource from:

| Environment variable | Maps to |
|----------------------|---------|
| `DB_JDBC_URL` | `spring.datasource.url` |
| `DB_USERNAME` | `spring.datasource.username` |
| `DB_PASSWORD` | `spring.datasource.password` |

Optional: `JAVA_OPTS` for JVM flags; `SPRING_PROPS` for nested Spring JSON overrides (same pattern as other product-plane services).

Release images are published as `opendatamesh/odm-platform-devops`.

## Checklist

1. Provision **PostgreSQL** (schema migrations run via Flyway on startup).
2. Inject `DB_*` (and optionally notification / `server.baseUrl`) — see [Configuration](configuration.md).
3. If notification integration is enabled, point `odm.product-plane.notification-service.address` at a reachable Notification Server and set `active: true`.
4. Expose Swagger / health as required by your platform.

---

↑ Back to [docs index](../README.md)
