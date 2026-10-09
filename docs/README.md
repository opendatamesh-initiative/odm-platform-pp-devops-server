# DevOps Server documentation

Index of guides for the **ODM Platform DevOps Server**.

<p>
  <a href="http://localhost:8002/swagger-ui.html">Swagger UI</a> ·
  <a href="http://localhost:8002/v3/api-docs">OpenAPI</a>
  <em>(when the service is running locally with the <code>dev</code> profile)</em>
</p>

---

## Service

| Guide | Description |
|:------|:------------|
| [Activity lifecycle](service/activity-lifecycle.md) | What activities and tasks are, and how execute → approve → run works |
| [Events](service/events.md) | How Notification drives approval and execution |
| [Policy service](service/policy-service.md) | Optional governance gate vs auto-approve |

Engineering conventions live in [`spdd/norms/`](../spdd/norms/README.md).

## Setup

| Guide | Description |
|:------|:------------|
| [Development](setup/development.md) | Local build, run, profiles, and testing |
| [Deployment](setup/deployment.md) | Docker / container deployment and external dependencies |
| [Configuration](setup/configuration.md) | Properties to manage (DB, Notification, Policy, executors) + minimal example |

---

↑ Back to the [project README](../README.md)
