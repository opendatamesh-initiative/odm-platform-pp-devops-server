# Configuration

Properties commonly managed for this service.

Related: [Development](development.md) · [Deployment](deployment.md)

## Database (Docker / Postgres profiles)

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/odm_service_template
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

The Docker image sets `PROFILES_ACTIVE=docker` by default (see `Dockerfile`).

Keep `spring.jpa.properties.hibernate.default_schema` aligned with Flyway (default `odm_service_template`; lowercase only).

## Notification service and observer identity

```yaml
odm:
  product-plane:
    notification-service:
      address: http://localhost:8001
      active: false

service-template:
  observer:
    name: service-template
    displayName: Service template
```

| Property | Description |
|----------|-------------|
| `odm.product-plane.notification-service.address` | Base URL of the ODM Platform Notification Server. Still required when `active` is `false` (placeholder resolution). |
| `odm.product-plane.notification-service.active` | If `true`, real HTTP client + startup connectivity check; if `false`, in-process no-op client |
| `service-template.observer.name` / `displayName` | Observer identity when subscribing. Rename the YAML prefix and the `@Value` keys in `NotificationClientConfig` together. |
| `server.baseUrl` | Public base URL of this service (observer callback base) |

Environment examples (relaxed binding):  
`ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

This template does **not** subscribe to event types at startup or expose an observer REST endpoint. Add those when you follow the Registry pattern (`NotificationClientEventSubscriber` + handlers).

## Server port

- Default in root `application.yml`: **8080**
- `dev` / `localpostgres`: **8087**
- Override with `server.port` or `SERVER_PORT`

---

↑ Back to [docs index](../README.md)
