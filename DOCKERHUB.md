# ODM Platform DevOps Server

The DevOps Server is the product-plane DevOps service of [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) V2. It manages the lifecycle of a data product version inside the physical infrastructure: it runs the version's activities (for example `dev`, then `prod`) and their tasks in order, asks for a policy approval before each activity and each task, and records and announces every outcome. It does not provision anything itself: each task runs a pipeline on an executor, which DevOps either starts and follows (full control) or lets report back on its own (instrumented). Approvals and outcomes travel through the Notification service.

- **Source code:** [opendatamesh-initiative/odm-platform-pp-devops-server](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server)
- **Documentation:** [docs](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/blob/main/docs/README.md)
- **Issues:** [GitHub issues](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/issues)

## Tags

Each [GitHub release](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/releases) publishes one image tagged with its version, without the `v` prefix:

```bash
docker pull opendatamesh/odm-platform-devops:<version>
```

There is no `latest` tag: always pin a version. Tags before `2.0.0` are ODM Platform V1 images, built from [odm-platform](https://github.com/opendatamesh-initiative/odm-platform), and are configured differently.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| PostgreSQL | Yes | Flyway creates and migrates the schema on startup. |
| [Notification service](https://hub.docker.com/r/opendatamesh/odm-platform-notification) | Yes, to run executions | DevOps registers itself as an observer and receives approvals and other events on `server.baseUrl`. Without it, executions stay `PENDING`. |
| Policy service | No | When it is disabled, DevOps approves every request automatically. |
| Executors | Yes, for every task you run | Each executor is declared in the configuration as `full-control` (with an address) or `instrumented`. |

## Quick start

Starts DevOps against an existing PostgreSQL database, with Notification and Policy disabled and no executors. Use it to check that the container starts and connects to the database: without Notification, executions stay `PENDING`.

```bash
docker run --name odm-devops -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://db.example.com:5432/devops \
  -e DB_USERNAME=devops \
  -e DB_PASSWORD=change-me \
  opendatamesh/odm-platform-devops:<version>
```

When it's up, the API (prefix `/api/v2/pp/devops/`) is documented at `http://localhost:8080/swagger-ui.html` (OpenAPI at `/v3/api-docs`).

## Production-like run

Spring Boot reads nested configuration from the `SPRING_APPLICATION_JSON` environment variable. This example enables Notification, keeps Policy disabled, and declares one full-control executor and one instrumented executor:

```bash
docker run --name odm-devops -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://db.example.com:5432/devops \
  -e DB_USERNAME=devops \
  -e DB_PASSWORD=change-me \
  -e SPRING_APPLICATION_JSON='{
    "server": { "baseUrl": "https://devops.example.com" },
    "odm": {
      "product-plane": {
        "notification-service": { "active": true, "address": "http://notification.example.com:8006" },
        "policy-service": { "active": false }
      },
      "utility-plane": { "executors": {
        "secrets-ttl": "1h",
        "services": {
          "starter": { "execution-mode": "full-control", "address": "http://executor.example.com:9080" },
          "cli": { "execution-mode": "instrumented" }
        }
      } }
    }
  }' \
  opendatamesh/odm-platform-devops:<version>
```

`server.baseUrl` must be a URL the Notification service can reach: it's where DevOps receives events. The executor names (`starter`, `cli`) are the names that tasks in the data product descriptor refer to.

## Environment variables

### Image

| Variable | Description | Default |
| --- | --- | --- |
| `DB_JDBC_URL` | JDBC URL of the PostgreSQL database (`spring.datasource.url`). | none, required |
| `DB_USERNAME` | Database user (`spring.datasource.username`). | none, required |
| `DB_PASSWORD` | Database password (`spring.datasource.password`). | none, required |
| `PROFILES_ACTIVE` | Spring profiles to activate. The `docker` profile maps the three `DB_*` variables above. | `docker` |
| `JAVA_OPTS` | Extra JVM options, for example `-Xmx512m`. | empty |
| `SPRING_PROPS` | Extra arguments added to the `java` command line before `-jar`, such as `-Dserver.port=9000`. Use it for `-D` arguments; for JSON configuration use `SPRING_APPLICATION_JSON`. | empty |

### Application

Every property can be set with `SPRING_APPLICATION_JSON` or as an environment variable, using Spring Boot relaxed binding (for example, `odm.product-plane.notification-service.active` becomes `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`). Executors are a map keyed by name, so declare them with `SPRING_APPLICATION_JSON`.

| Property | Description | Default |
| --- | --- | --- |
| `server.port` | HTTP port. | `8080` |
| `server.baseUrl` | Externally reachable URL of DevOps, used as the Notification observer callback. Required when Notification is active. | `http://localhost:8080` |
| `spring.jpa.properties.hibernate.default_schema` | Database schema used by Hibernate and Flyway. Lowercase only. | `odm_devops` |
| `odm.product-plane.notification-service.active` | Send and receive events through the Notification service. When `true`, DevOps checks the connection on startup. | `false` |
| `odm.product-plane.notification-service.address` | Base URL of the Notification service. | `http://localhost:8001` |
| `odm.product-plane.policy-service.active` | Wait for a Policy decision on each request (`true`) or approve automatically (`false`). | `false` |
| `odm.product-plane.policy-service.version` | With Policy active, `1` makes DevOps ask the Policy Service V1 at `address` and publish the decision itself. With any other value, DevOps waits for an external service to publish the decision on Notification. | `1` |
| `odm.product-plane.policy-service.address` | Base URL of the Policy service, used with `version` `1`. | `http://localhost:8005` |
| `devops.observer.name` | Observer name registered in Notification. Change it only when running several DevOps servers. | `devops` |
| `devops.observer.displayName` | Observer display name registered in Notification. | `DevOps` |
| `odm.utility-plane.executors.services.<name>.execution-mode` | `full-control` or `instrumented`. Required for each executor. | none |
| `odm.utility-plane.executors.services.<name>.address` | Base URL of the executor. Required for `full-control`; DevOps never calls an instrumented executor. | none |
| `odm.utility-plane.executors.polling-interval` | Time between status reads of a running full-control task. | `30s` |
| `odm.utility-plane.executors.secrets-ttl` | How long executor secrets received with a request stay in memory. Set it longer than your longest pipeline: once secrets expire, DevOps can no longer follow or cancel the run. | `1h` |

With no executor declared, DevOps starts but no task can run. The full list is in [Configuration](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/blob/main/docs/setup/configuration.md).

## Ports and volumes

- **Port:** `8080` (HTTP), or the value of `server.port`.
- **Volumes:** none. All state is in PostgreSQL; executor secrets are kept only in memory.

## Learn more

- [How DevOps works](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/blob/main/docs/service/how-devops-works.md)
- [Events](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/blob/main/docs/service/events.md)
- [Deployment guide](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/blob/main/docs/setup/deployment.md)

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
