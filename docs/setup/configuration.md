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
| `devops.observer.name` / `displayName` | Observer identity when subscribing |
| `server.baseUrl` | Public base URL of this service (observer callback base) |
| `odm.product-plane.policy-service.active` | If `false` or unset, every execution request is auto-approved. If `true`, DevOps waits and does not approve by itself |

Environment examples (relaxed binding):  
`ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

When Notification is active, DevOps subscribes at startup to the execution messages it handles and receives them on the observer notifications endpoint. With Notification inactive, an executed activity stays pending. See [Events](../service/events.md) and [Policy service](../service/policy-service.md).

## Executors

Declare each executor by name, with the address DevOps calls and an execution mode of full control or instrumented. This story runs only executors declared in full-control mode. An empty declaration list means no executor can be used.

```yaml
odm:
  utility-plane:
    executor-services:
      starter:
        address: http://localhost:9080
        execution-mode: full-control
```

DevOps polls a running task until it ends. The wait starts at half a second and doubles up to one minute. Shorten both limits in tests.

```yaml
odm:
  utility-plane:
    executor-polling:
      initial-delay: 500ms
      max-delay: 60s
```

Each running task holds one background thread for the whole run. The pool is bounded:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 32
        queue-capacity: 100
```

## Secrets and parameters

On execute, a secret for one executor arrives as a request header named `x-odm-<executorName>-executor-secret-<secretType>`. DevOps keeps it in memory for at most one hour, sends it only to that executor for that activity as `x-odm-<secretType>`, and drops it when the activity ends. Secrets are never stored, logged, returned, or put in events.

Executor parameters and pipeline parameters are not secret. They are stored, returned, and included in events. Pipeline parameter values may contain placeholders; those are filled in only when a task is about to start.

## Server port

- Default in root `application.yml`: **8080**
- `dev` / `localpostgres`: **8002**
- Override with `server.port` or `SERVER_PORT`

---

↑ Back to [docs index](../README.md)
