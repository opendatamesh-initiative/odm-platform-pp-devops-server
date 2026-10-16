# Backward Compatibility Package (`old`)

Temporary adapters that let DevOps work with services that still speak a previous API version.
Story: BDMD-5438. Analysis: `spdd/analysis/BDMD-5438-202610131149-[Analysis]-policy-v1-adapter.md` (Revision 7).
Canvas: `spdd/prompt/BDMD-5438-202610141700-[Feat]-service-policy-v1-adapter.md`.

**This package is temporary.** It is deleted when Policy V2 can read the V2 events.
Deleting the `old` package and the Policy `version` and `address` properties removes the adapter.

## Dependency rules

- Code in `old` may depend on code outside it. The Policy V1 adapter reads activities through the core `ActivityService`.
- Code outside `old` must not depend on code inside it. Core use cases and the auto-approvers are not modified.

## Policy V1 adapter (`old.v1.policyservice`)

When Policy is active at version 1, DevOps sends its two execution requests to Policy Service V1
(`POST {address}/api/v1/pp/policy/validation`, `storeResults=true`) and answers each one with the V2
approval or rejection that the existing use cases already accept. The adapter never calls a use case and never writes status.

### Modes

Properties: `odm.product-plane.policy-service.active`, `.version`, `.address`.

| Property combination | Who answers the requests | Policy called |
| --- | --- | --- |
| `active=false` (default) | Auto-approvers (core) | No |
| `active=true`, `version=1` | The adapter | Yes |
| `active=true`, any other version | Nobody. Work stays `PENDING` | No |

### Event map

| V2 request | Policy `event` and `resourceType` | `afterState` (policy input) | Decision events |
| --- | --- | --- | --- |
| `ACTIVITY_EXECUTION_REQUESTED` | `ACTIVITY_EXECUTION_RESULT` | `{ activity, otherActivities }` (DD-10) | `ACTIVITY_EXECUTION_APPROVED` / `ACTIVITY_EXECUTION_REJECTED` |
| `ACTIVITY_TASK_EXECUTION_REQUESTED` | `TASK_EXECUTION_RESULT` | `{ activity, task }` (DD-10) | `ACTIVITY_TASK_EXECUTION_APPROVED` / `ACTIVITY_TASK_EXECUTION_REJECTED` |

- `currentState` is omitted.
- `dataProductId` is `UUID.nameUUIDFromBytes(fqn UTF-8)`, the same value the DPDS parser 1.x computes (DD-7). `dataProductVersion` is the version tag.
- Only a failed policy with `blockingFlag=true` rejects. Empty results and non-blocking failures approve. A `ClientException` (Policy unreachable or HTTP error) rejects (DD-6).
- A missing activity, or a missing data product version uuid or fqn, is a defect. The notification fails, no decision is emitted, and the work stays `PENDING` (DD-6, DD-10).
- Not sent: `ACTIVITY_STAGE_TRANSITION`, activity succeeded/failed/canceled, and V1 notification broadcasts.

### The policy input

The `afterState` document is built by `PolicyV1InputEnricher` from the V2 event content. The domain events do not change.

**Activity request (`ACTIVITY_EXECUTION_RESULT`)**

```json
{
  "activity": {
    "uuid": "...",
    "dataProductVersionUuid": "...",
    "dataProductFqn": "urn:org.opendatamesh:dataproducts:sample-product",
    "dataProductVersionTag": "1.0.0",
    "name": "prod",
    "sortOrder": 2,
    "status": "PENDING",
    "tasks": [ "... as the V2 event carries them ..." ]
  },
  "otherActivities": [
    {
      "uuid": "...",
      "name": "dev",
      "sortOrder": 1,
      "status": "SUCCEEDED",
      "startedAt": "2026-10-15T15:19:00.123+00:00",
      "finishedAt": "2026-10-15T15:20:00.456+00:00"
    }
  ]
}
```

- `otherActivities` holds the other activities of the same data product version, without the requested one. Only `uuid`, `name`, `sortOrder`, `status`, `startedAt`, `finishedAt`. No tasks.
- Sorted by `sortOrder`, ascending, nulls last. `[]` when there are none. The key is always present.

**Task request (`TASK_EXECUTION_RESULT`)**

```json
{
  "activity": {
    "uuid": "...",
    "name": "prod",
    "sortOrder": 2,
    "status": "RUNNING",
    "tasks": [
      {
        "uuid": "...",
        "name": "build",
        "sortOrder": 0,
        "status": "SUCCEEDED",
        "startedAt": "2026-10-15T15:19:00.123+00:00",
        "finishedAt": "2026-10-15T15:19:30.000+00:00",
        "results": [
          { "uuid": "...", "content": "...", "generatedAt": "2026-10-15T15:19:29.000+00:00" }
        ]
      }
    ]
  },
  "task": { "... the requested task, as the V2 event carries it ..." }
}
```

- `activity.tasks` holds all tasks of the activity, including the requested one, with their `results`. Sorted by `sortOrder`, nulls last.
- `logs` is removed from every task. The requested task has no results yet, so its `results` is `[]`.
- All other activity fields come from the event unchanged.

**Dates.** Every date in the document (`startedAt`, `finishedAt`, `generatedAt`) is an ISO-8601 text value with milliseconds and a UTC offset, for example `2026-10-15T15:19:00.123+00:00`. It is the same format the notification event serialization uses. No epoch numbers are mixed in.

### Known limitations

- **Filtering expressions (DD-8, DD-11).** Policies on `ACTIVITY_EXECUTION_RESULT` and `TASK_EXECUTION_RESULT` may define a filtering expression. It is evaluated on the JSON above, with the V2 field names and the V2 status values. This needs the Policy server change of DD-11 deployed. On a Policy server without that change, the V1 conversion rejects the V2 content with a 400 (`SC400_05_MALFORMED_INPUT_OBJECT`) and the adapter rejects the request.
- **Expressions are limited.** Filters can read fields and call methods of strings, lists, and maps. They cannot use `T(...)`, constructors, or the getters of the old V1 typed classes (DD-11).
- **No lock on the context.** The activity context is read when the request is handled. Two requests that arrive together can both pass a check such as "no other activity is `RUNNING`". The use cases still hold the status, so this is a race on the policy decision, not on the status.
- **No size limit on results.** `activity.tasks[*].results[*].content` is sent in full with every task request. Policy stores the whole input for each evaluation.
- **V2 names and values.** `afterState` keeps V2 field names (`sortOrder`, `dataProductFqn`, ...) and V2 status values. Old V1 policy documents are not supported.
- **`resourceType`.** Policy V1 declares `ACTIVITY_EXECUTION_RESULT` and `TASK_EXECUTION_RESULT` as both `EventType` and `ResourceType`. The adapter sets both to the same name. The Policy server selects policies by `event` only.
- **Policy configuration.** `active`, `version` and `address` must be set before the adapter loads.
- **Notification is required.** Decisions are emitted on the notification bus. With the Notification service inactive nothing is delivered and the work stays `PENDING`.
