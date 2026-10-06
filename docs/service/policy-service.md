# Policy service integration

High-level view of how Policy participates in activity and task approval.

Related:

- [Service](README.md) — what an activity execution does
- [Events](events.md) — the messages Policy and DevOps share
- [Configuration](../setup/configuration.md) — the Policy flag and Notification

## Role

Policy is the optional governance gate on activity execution and on each task execution.

After DevOps creates an activity as pending, or is ready to run the next task, it asks for approval. Something must approve that request:

- **Policy active** — an external Policy service is expected to decide. DevOps does not call Policy and does not evaluate the request. An approval starts the activity or the task. A rejection fails the activity, or fails that task and then the activity. A refusal is not a user cancel.
- **Policy inactive** — DevOps approves the request itself, with no checks.

In both cases, Notification carries the messages. DevOps applies the approval when the approved message comes back.

Policy does not replace Notification. Notification must be active for the loop to close. With Notification inactive, the activity stays pending.

## Active vs inactive

| Mode | When | What happens |
|------|------|--------------|
| **Active** | the Policy flag is turned on | DevOps waits. It does not approve the request itself. An approval message starts the work. A rejection message fails it. |
| **Inactive** | the Policy flag is off, or unset | DevOps auto-approves every activity execution request and every task execution request |

```text
Execute / next task
        │
        ▼
  approval requested
        │
        ├── Policy inactive → DevOps approves with no checks
        │
        ▼
  the decision comes back
        │
        ├── approved → activity starts, or the task runs
        │
        └── rejected → the activity fails, and this is not a user cancel
```

## What you need

- Notification active and reachable, and this service reachable by Notification, whenever the loop should close
- Policy off for the auto-approval path
- Policy on when an external service will approve or refuse. DevOps applies that message. It does not evaluate policies itself
