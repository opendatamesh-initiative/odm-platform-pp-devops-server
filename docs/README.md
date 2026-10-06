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
| [Service](service/README.md) | What an activity is, and how full-control execution proceeds |
| [Policy service](service/policy-service.md) | Auto-approval while Policy is inactive, and a refusal while it is active |
| [Events](service/events.md) | Messages that move an execution along |

Engineering conventions live in [`spdd/norms/`](../spdd/norms/README.md).

## Setup

| Guide | Description |
|:------|:------------|
| [Development](setup/development.md) | Local build, run, profiles, and testing |
| [Deployment](setup/deployment.md) | Docker / container deployment |
| [Configuration](setup/configuration.md) | Properties to manage (DB, Notification, Policy, executors) |

---

↑ Back to the [project README](../README.md)
