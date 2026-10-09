# Events

High-level view of how DevOps uses the Notification service to run an activity, plus the catalogue of messages.

Related:

- [Activity lifecycle](activity-lifecycle.md) — states these messages move
- [Policy service](policy-service.md) — who approves a request
- [Configuration](../setup/configuration.md) — Notification and observer settings

## Role of events

DevOps does not finish an execution inside the execute call. It **emits** a request when an activity is created or a task is ready, and **reacts** when an approval or rejection arrives.

The Notification service is the bus:

- DevOps **publishes** requests and outcomes
- While Policy is inactive, DevOps also publishes the approval
- Notification **delivers** those messages back to the DevOps observer

Without Notification active, that loop does not run and the activity stays `PENDING`.

## How DevOps joins the bus

At startup, when Notification is active, DevOps:

1. Checks that Notification is reachable
2. Registers as an **observer** (callback URL = `server.baseUrl`, plus observer name and display name)
3. Subscribes to the messages it handles: activity execution requested, approved, and rejected; task execution requested, approved, and rejected

Inbound messages arrive at `POST /api/v2/up/observer/notifications`. Property details: [Configuration](../setup/configuration.md).

```text
DevOps emits *_REQUESTED
        │
        ▼
  Policy or auto-approve
        │
        ▼
DevOps receives *_APPROVED / *_REJECTED
        │
        ▼
  the activity starts, or the task runs
        │
        ▼
  the next full-control task is requested, a caller asks for an instrumented task, or the activity succeeds, fails, or is canceled
```

`ACTIVITY_TASK_EXECUTION_REQUESTED` is the same message in both modes. DevOps emits it when the next task is full control. The caller causes it when asking to run an instrumented task.

## Event catalogue

| Event type | Emitted by | Consumed by | What it carries |
| --- | --- | --- | --- |
| `ACTIVITY_EXECUTION_REQUESTED` | DevOps, when an activity is created for execution | DevOps auto-approval, while Policy is inactive | The activity, its position among the data product version's activities, and its tasks, without logs or results |
| `ACTIVITY_EXECUTION_APPROVED` | DevOps auto-approval, or Policy | DevOps, which starts the activity | The activity identifier, name, position, and data product version |
| `ACTIVITY_EXECUTION_REJECTED` | Policy | DevOps, which fails the activity | The activity identifier, name, position, and data product version |
| `ACTIVITY_TASK_EXECUTION_REQUESTED` | DevOps, for the next full-control task; also when a caller asks to run an instrumented task | DevOps auto-approval, while Policy is inactive | The activity without its tasks, and the one task to run, without logs or results |
| `ACTIVITY_TASK_EXECUTION_APPROVED` | DevOps auto-approval, or Policy | DevOps, which runs that task | The activity block above, and the task identifier and name |
| `ACTIVITY_TASK_EXECUTION_REJECTED` | Policy | DevOps, which fails that task | The activity block above, and the task identifier and name |
| `ACTIVITY_SUCCEEDED` | DevOps, when every task has succeeded | External listeners | The finished activity, without logs or results |
| `ACTIVITY_FAILED` | DevOps, when a task has failed and the rest that had not started are canceled, and when Policy refuses an execution | External listeners | The finished activity, without logs or results |
| `ACTIVITY_CANCELED` | DevOps, when a user cancel closes the activity | External listeners | The finished activity, without logs or results |

Every message is about one activity. It includes that activity's position. It does not include logs, results, secrets, or the other activities of the data product version.

Executor parameters and pipeline parameters may appear on the activity and task inside a request. They are not secret. Secret values never appear in a message.
