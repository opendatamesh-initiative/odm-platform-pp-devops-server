# Configuration

Properties commonly managed for this service.

Related: [Development](development.md) · [Deployment](deployment.md)

## Database (Docker / Postgres profiles)

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/odm_devops
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

The Docker image sets `PROFILES_ACTIVE=docker` by default (see `Dockerfile`).

Keep `spring.jpa.properties.hibernate.default_schema` aligned with Flyway (default `odm_devops`; lowercase only). Tests use `odm_devops_test`.

## Notification service and observer identity

```yaml
odm:
  product-plane:
    notification-service:
      address: http://localhost:8001
      active: false

devops:
  observer:
    name: devops
    displayName: DevOps
```

| Property | Description |
|----------|-------------|
| `odm.product-plane.notification-service.address` | Base URL of the ODM Platform Notification Server. Still required when `active` is `false` (placeholder resolution). |
| `odm.product-plane.notification-service.active` | If `true`, real HTTP client + startup connectivity check; if `false`, in-process no-op client |
| `devops.observer.name` / `displayName` | Observer identity when subscribing. Keep in sync with `@Value` keys in `NotificationClientConfig`. |
| `server.baseUrl` | Public base URL of this service (observer callback base) |

Environment examples (relaxed binding):  
`ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

This service does **not** subscribe to event types at startup or expose an observer REST endpoint. Add those when a later story needs inbound notifications.

## Server port

- Default in root `application.yml`: **8080**
- `dev` / `localpostgres`: **8002**
- Override with `server.port` or `SERVER_PORT`

---

↑ Back to [docs index](../README.md)
