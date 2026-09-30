# Policy service integration

High-level view of how Policy participates in activity and task approval.

Related:

- [Service](README.md) — what an activity execution does
- [Events](events.md) — the messages Policy and DevOps share
- [Configuration](../setup/configuration.md) — the Policy flag and Notification

## Role

Policy is the optional governance gate on activity execution and on each task execution.

After DevOps creates an activity as pending, or is ready to run the next task, it asks for approval. Something must approve that request:

- **Policy active** — an external Policy service is expected to decide. This story does not call Policy and does not handle a rejection.
- **Policy inactive** — DevOps approves the request itself, with no checks and no rejection.

In both cases, Notification carries the messages. DevOps applies the approval when the approved message comes back.

Policy does not replace Notification. Notification must be active for the loop to close. With Notification inactive, the activity stays pending.

## Active vs inactive

| Mode | When | What happens |
|------|------|--------------|
| **Active** | the Policy flag is turned on | DevOps waits. It does not approve the request itself. |
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
  approved message comes back
        │
        ▼
  activity starts, or the task runs
```

## What you need

- Notification active and reachable, and this service reachable by Notification, whenever the loop should close
- Policy off for the auto-approval path used by this story
- Policy on only when a later story evaluates real policies
