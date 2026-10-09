# Policy service integration

High-level view of how Policy participates in activity and task approval.

Related:

- [Activity lifecycle](activity-lifecycle.md) — states Policy decisions change
- [Events](events.md) — the messages Policy and DevOps share
- [Configuration](../setup/configuration.md) — the Policy flag and Notification

## Role

Policy is the optional **governance gate** on activity execution and on each task execution.

After DevOps creates an activity as `PENDING`, or is ready to run the next task, it emits a `*_REQUESTED` message. Something must approve that request:

- **Policy active** — an external Policy service is expected to decide. DevOps does not call Policy and does not evaluate the request. An approval starts the activity or the task. A rejection fails the activity, or fails that task and then the activity. A refusal is separate from a user cancel.
- **Policy inactive** — DevOps approves the request itself, with no checks.

In both cases, Notification carries the messages. DevOps applies the decision when the approved or rejected message comes back.

Policy does not replace Notification. Notification must be active for the loop to close. With Notification inactive, the activity stays `PENDING`.

## Active vs inactive

| Mode | When | What happens |
|------|------|--------------|
| **Active** | `odm.product-plane.policy-service.active=true` | DevOps waits. An approval message starts the work. A rejection message fails it. |
| **Inactive** | `active=false`, or unset | DevOps auto-approves every activity execution request and every task execution request |

```text
Execute / next task → *_REQUESTED
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
  Policy active               Policy inactive
  external decision           auto-approve
        │                           │
        └─────────────┬─────────────┘
                      ▼
              APPROVED or REJECTED
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
  activity starts,            the activity fails
  or the task runs
```

## What you need to configure

- Notification active and reachable, and `server.baseUrl` reachable by Notification, whenever the loop should close
- Policy off (`active=false`) for the auto-approval path; no Policy address is required
- Policy on when an external service will approve or refuse. DevOps applies that message. It does not evaluate policies itself

How this maps onto messages: [Events](events.md). Property names: [Configuration](../setup/configuration.md).
