# Events

High-level view of how DevOps uses Notification to run an activity, and the catalogue of messages.

Related:

- [Service](README.md) — the full-control process
- [Policy service](policy-service.md) — who approves a request
- [Configuration](../setup/configuration.md) — Notification, the observer, and executors

## Role of events

DevOps does not finish an execution inside the execute call. It publishes a request, and later reacts when an approval arrives.

Notification is the bus:

- DevOps publishes requests and outcomes
- While Policy is inactive, DevOps also publishes the approval
- Notification delivers those messages back to DevOps

Without Notification active, that loop does not run and the activity stays pending.

## How DevOps joins the bus

At startup, when Notification is active, DevOps registers as an observer and subscribes to the messages it handles: activity execution requested, activity execution approved, task execution requested, and task execution approved.

Inbound messages arrive at the observer notifications endpoint. Rejection messages are not subscribed and are not handled.

```text
DevOps emits an execution request
        │
        ▼
  auto-approve, while Policy is inactive
        │
        ▼
DevOps receives the approval
        │
        ▼
  the activity starts, or the task runs
        │
        ▼
  the next task is requested, or the activity succeeds or fails
```

## Event catalogue

| Message | Emitted by | Consumed by | What it carries |
| --- | --- | --- | --- |
| Activity execution requested | DevOps, when an activity is created for execution | DevOps auto-approval, while Policy is inactive | The activity, its position among the data product version's activities, and its tasks, without logs or results |
| Activity execution approved | DevOps auto-approval, or Policy in a later story | DevOps, which starts the activity | The activity identifier, name, position, and data product version |
| Task execution requested | DevOps, when the next task may start | DevOps auto-approval, while Policy is inactive | The activity as above, and the one task to run, without logs or results |
| Task execution approved | DevOps auto-approval, or Policy in a later story | DevOps, which runs that task | The activity block above, and the task identifier and name |
| Activity succeeded | DevOps, when every task has succeeded | External listeners | The finished activity, without logs or results |
| Activity failed | DevOps, when a task has failed and the rest that had not started are canceled | External listeners | The finished activity, without logs or results |
| Activity canceled | Reserved for a later story | Not handled here | Not emitted by this process |
| Activity execution rejected | Not emitted here | Not handled | Not subscribed |
| Task execution rejected | Not emitted here | Not handled | Not subscribed |

Every message is about one activity. It includes that activity's position. It does not include logs, results, secrets, or the other activities of the data product version.

Executor parameters and pipeline parameters may appear on the activity and task inside a request. They are not secret. Secret values never appear in a message.
