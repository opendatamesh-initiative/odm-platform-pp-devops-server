# Configuration

Properties you **must** or **should** set for the DevOps Server, including database, product-plane integration, and executors. Framework defaults (Flyway script locations, Hibernate `ddl-auto`, banners, logging patterns, and similar) are left to the application YAML and are not listed here.

Related: [Development](development.md) · [Deployment](deployment.md) · [Activity lifecycle](../service/activity-lifecycle.md) · [Events](../service/events.md) · [Policy service](../service/policy-service.md)

## Minimal production-like configuration

Complete minimal example for containers (Notification on, Policy off, one full-control executor and one instrumented executor). Change URLs and credentials for your environment.

```yaml
server:
  port: 8080
  baseUrl: https://devops.example.com # required: observer callback base URL

spring:
  datasource:
    url: jdbc:postgresql://db:5432/odm_devops
    username: your_username
    password: your_password
  jpa:
    properties:
      hibernate:
        default_schema: odm_devops # only schema setting to configure (Flyway uses this)

odm:
  product-plane:
    notification-service:
      address: http://notification-service:8001
      active: true
    policy-service:
      active: false
  utility-plane:
    executor-services:
      starter:
        address: http://executor:9080
        execution-mode: full-control
      cli:
        execution-mode: instrumented
    executor-polling:
      interval: 30s
    executor-secrets:
      ttl: 1h
```

When Policy is **active**, set `odm.product-plane.policy-service.active: true`. DevOps still does not call Policy; an external service approves or rejects on the Notification bus. Keep Notification active for the event loop. With Notification inactive, an executed activity stays `PENDING`.

### Equivalent `SPRING_PROPS` (containers)

The image passes `SPRING_PROPS` through as extra JVM arguments. Use Spring's JSON override:

```bash
docker run -p 8080:8080 \
  -e SPRING_PROPS='-Dspring.application.json={"server":{"port":8080,"baseUrl":"https://devops.example.com"},"spring":{"datasource":{"url":"jdbc:postgresql://db:5432/odm_devops","username":"your_username","password":"your_password"},"jpa":{"properties":{"hibernate":{"default_schema":"odm_devops"}}}},"odm":{"product-plane":{"notification-service":{"address":"http://notification-service:8001","active":true},"policy-service":{"active":false}},"utility-plane":{"executor-services":{"starter":{"address":"http://executor:9080","execution-mode":"full-control"},"cli":{"execution-mode":"instrumented"}}}}}' \
  odm-platform-pp-devops-server
```

---

## Server

| Property | Purpose | Default | Manage when |
| --- | --- | --- | --- |
| `server.baseUrl` | Externally reachable base URL used as the Notification **observer callback** | `http://localhost:8080` in base YAML; `http://localhost:8002` on `dev` and `localpostgres` | **Must** set to a URL Notification can reach whenever Notification is active |
| `server.port` | HTTP listen port | `8080` (`dev` and `localpostgres` use `8002`) | **Should** set if you do not use the default |

```yaml
server:
  port: 8080
  baseUrl: https://devops.example.com
```

Override the port with `server.port` or `SERVER_PORT`.

## Database

Production uses **PostgreSQL**. Local `dev` can use **H2** (in-memory, PostgreSQL compatibility mode) via the profile — see [Development](development.md).

| Property | Purpose | Default | Manage when |
| --- | --- | --- | --- |
| `spring.datasource.url` | JDBC URL | profile-specific | **Must** for any real database |
| `spring.datasource.username` | DB user | profile-specific | **Must** |
| `spring.datasource.password` | DB password | profile-specific | **Must** (may be empty for H2) |
| `spring.jpa.properties.hibernate.default_schema` | Schema for Hibernate **and** Flyway | `odm_devops` | **Must** set intentionally; lowercase only |

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/odm_devops
    username: your_username
    password: your_password
  jpa:
    properties:
      hibernate:
        default_schema: odm_devops
```

| Environment | Engine | Schema |
| --- | --- | --- |
| Local `dev` | H2 in-memory | `odm_devops` |
| Tests | PostgreSQL (Testcontainers) | `odm_devops_test` |
| Local Postgres / production | PostgreSQL | `odm_devops` (or your agreed lowercase name) |

The `docker` profile binds the datasource from `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD` (see [Deployment](deployment.md)).

## Notification service

Required for the event-driven execution loop. Behavior: [Events](../service/events.md).

| Property | Purpose | Default | Manage when |
| --- | --- | --- | --- |
| `odm.product-plane.notification-service.active` | Enable the Notification client (`true`) or the no-op client (`false`) | `false` | **Must** set intentionally |
| `odm.product-plane.notification-service.address` | Notification service base URL | `http://localhost:8001` | **Must** when `active=true`. Still required when `active` is `false` (placeholder resolution). |

```yaml
odm:
  product-plane:
    notification-service:
      active: true
      address: http://notification-service:8001
```

When Notification is active, DevOps subscribes at startup to the execution messages it handles and receives them on the observer notifications endpoint.

## Policy service

Optional governance gate. When inactive, DevOps auto-approves `*_REQUESTED`. Behavior: [Policy service](../service/policy-service.md).

| Property | Purpose | Default | Manage when |
| --- | --- | --- | --- |
| `odm.product-plane.policy-service.active` | Wait for an external Policy decision (`true`) or auto-approve (`false`) | `false` | **Must** set intentionally |

```yaml
odm:
  product-plane:
    policy-service:
      active: false
```

DevOps does not call a Policy URL. With the flag on, an external service publishes the approval or rejection on Notification.

## Observer identity

Used when registering with Notification. Defaults are fine for a single DevOps server; override if you run multiple observers.

| Property | Purpose | Default |
| --- | --- | --- |
| `devops.observer.name` | Unique observer name in Notification | `devops` |
| `devops.observer.displayName` | Human-readable name | `DevOps` |

`server.baseUrl` is still the callback address (see [Server](#server)).

## Executors

Declare each executor by name and an execution mode of `full-control` or `instrumented`. An empty declaration list means no executor can be used. Behavior of the two modes: [Activity lifecycle](../service/activity-lifecycle.md).

| Property | Purpose | Manage when |
| --- | --- | --- |
| `odm.utility-plane.executor-services.<name>.execution-mode` | `full-control` or `instrumented` | **Must** for each executor you intend to use |
| `odm.utility-plane.executor-services.<name>.address` | Base URL DevOps calls | **Must** for `full-control`. Optional for `instrumented`. DevOps calls a full-control address and does not call an instrumented address, whether that address is set or omitted. |

```yaml
odm:
  utility-plane:
    executor-services:
      starter:
        address: http://localhost:9080
        execution-mode: full-control
      cli:
        execution-mode: instrumented
```

### Full-control polling

DevOps polls a running full-control task until it ends. It does not poll an instrumented task. The status poll is one interval, with no back-off. The poll stops after the secrets lifetime divided by that interval.

| Property | Purpose | Default |
| --- | --- | --- |
| `odm.utility-plane.executor-polling.interval` | Time between status reads | `30s` |

Each running task holds one background thread for the whole run. The pool is bounded:

| Property | Purpose | Default |
| --- | --- | --- |
| `spring.task.execution.pool.core-size` | Core pool size | `8` |
| `spring.task.execution.pool.max-size` | Maximum pool size | `32` |
| `spring.task.execution.pool.queue-capacity` | Queue capacity | `100` |

## Secrets and parameters

On execute, a secret for one executor arrives as a request header named `x-odm-<executorName>-executor-secret-<secretType>`. DevOps keeps the secret in memory for the configured lifetime, sends it only to that executor for that activity as `x-odm-<secretType>`, and drops it when the activity ends. Secrets are never stored, logged, returned, or put in events.

| Property | Purpose | Default |
| --- | --- | --- |
| `odm.utility-plane.executor-secrets.ttl` | How long a secret stays in memory | `1h` |

Executor parameters and pipeline parameters are not secret. They are stored, returned, and included in events. Pipeline parameter values may contain placeholders; those are filled in only when a task is about to start.

## How to pass configuration

| Mechanism | Typical use |
| --- | --- |
| Profile YAML (`application-*.yml`) | Local development |
| Env vars (`SPRING_DATASOURCE_URL`, `SERVER_BASE_URL`, …) | Simple overrides; Spring relaxed binding applies (e.g. `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`) |
| `SPRING_PROPS` | Containers — extra JVM arguments, typically `-Dspring.application.json=...` (see [minimal example](#minimal-production-like-configuration)) |
| `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` | Datasource when the `docker` profile is active |

---

↑ Back to [docs index](../README.md)
