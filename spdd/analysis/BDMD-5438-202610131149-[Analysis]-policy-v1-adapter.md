# SPDD Analysis: Policy V1 Adapter for DevOps

Story **BDMD-5438**, "Adapter layer policy V1", for `odm-platform-pp-devops-server`, and the Policy server change it depends on in `odm-platform`.

**Status (2026-10-15).** Revision 7 is implemented in DevOps and in the Policy server (DD-11). The DevOps unit and integration tests pass. A live end-to-end run against Policy is still to be done. Decisions stay in force until Policy V2 can build the policy input itself.

**Revision 7 (2026-10-15).** The policy document is now a contract that the adapter builds, not the V2 event content. The domain events are unchanged. The adapter adds `otherActivities` to the activity request, and `activity.tasks` with their results (and without their logs) to the task request (DD-10). Filtering expressions are supported on the two result names, against that contract, once the Policy server evaluates filters on raw JSON (DD-8, DD-11). This changes DD-2, DD-8, and DD-9, and adds DD-10 and DD-11. The reasons for keeping the domain events unchanged are in section 4 of `agent-reports/event-design-domain-vs-integration-events.md`. Statements in revisions 5 and 6 that contradict revision 7 are marked as superseded in place.

**Revision 6 (2026-10-14).** Clarification pass, no change of design. Added the decision log, the known limitations, DD-9 (the task-field rule, previously implicit), and the open items for REASONS. Every rule now has an ID that can be cited. *Superseded in part by revision 7: DD-2, DD-8, DD-9. Filtering is no longer unsupported, and the task request now carries results through `activity.tasks`.*

**Revision 5 (2026-10-14).** Three changes from the review. The fqn-derived id is computed with a JDK call, so no parser dependency is added (DD-7). The status keeps its V2 names, and filtering expressions are not supported, so policies never depend on V1 status names (DD-8). The task is sent unchanged, with its `results` and `logs` (DD-9). This also corrects revision 4: the `IdentifierStrategyFactory` class is in the 1.x parser artifact, not the 2.x one. *Superseded in part by revision 7: DD-2 (no longer the V2 content as is), DD-8 (filtering is supported), and DD-9 (the requested task carries no logs or results, because the V2 events do not carry them).*

**Revision 4 (2026-10-14).** `dataProductId` and `dataProductVersion` are populated from the activity, as the Registry does (DD-7). `afterState` keeps the V2 names. The filtering risk is checked against the Policy source. Superseded in part by revision 5: filtering expressions are not supported (DD-8). Superseded in part by revision 7: DD-8.

**Revision 3 (2026-10-13).** Design decisions from the review. The adapter lets DevOps V2 run while Policy is still V1. It is removed when Policy V2 can read the V2 events itself. The goal is not to keep old Policy documents working. The two V2 requests are sent to Policy under the V1 result names. `afterState` is the V2 `eventContent`, with the V2 field names left as they are. `ACTIVITY_STAGE_TRANSITION` is not sent. *Superseded in part by revision 7: DD-2.*

**Revision 2 (2026-10-13).** Compared Blueprint `old/v1`. Superseded for the Registry-read question. This story does not call Registry.

**Revision 1 (2026-10-13).** First pass. Proposed mapping only `ACTIVITY_STAGE_TRANSITION`, and leaving the two result checks unmapped. Superseded by revision 3.

The Registry reference is `agent-reports/registry-v1-policy-adapter.md`. The reject and approve use cases this adapter relies on are in `spdd/analysis/BDMD-5440-202610011324-[Analysis]-full-control-cancel-error-paths.md`. Rejection applies only while the activity or the task is `PENDING`.

## Decision Log

Each decision has an ID. Cite the ID in code, tests, and review comments. All decisions are accepted for now and are revisited when Policy V2 builds the policy input itself.

| ID | Decision | Reason in one line |
|----|----------|--------------------|
| DD-1 | The two V2 requests are sent under the V1 result names `ACTIVITY_EXECUTION_RESULT` and `TASK_EXECUTION_RESULT`, at request time | The existing rejection still applies while the status is `PENDING` |
| DD-2 | `afterState` is the policy input contract, built by the adapter from the V2 `eventContent`. It is `{ activity, otherActivities }` for an activity and `{ activity, task }` for a task. `currentState` is omitted. *Changed in revision 7.* | The domain events stay as they are, so they do not limit what policies can check. Policy stores the JSON as is. An empty `currentState` skips the DevOps V1 enricher |
| DD-3 | No renaming of V2 fields, no Registry read, no descriptor. The V2 status values are kept too (DD-8) | Old V1 documents are not served by this story |
| DD-4 | The adapter emits the decision on the bus and never calls the use case. One answerer per request | Same pattern as the Registry |
| DD-5 | One package, not imported by core. Handlers are built by conditional configuration | Same pattern as the Registry. The package is deleted with the adapter |
| DD-6 | A failed blocking policy rejects. A non-blocking failure approves. An unreachable Policy rejects. A defect fails the notification | Same policy as the Registry adapter |
| DD-7 | `dataProductId` is the JDK name-based UUID of the fqn. `dataProductVersion` is the version tag | Policy stores and searches by this legacy id. No dependency is needed |
| DD-8 | Filtering expressions are supported on the two result names, against the policy input contract, with V2 field names and V2 status values. The execution status is not translated. *Flipped in revision 7.* | Policies can check the V2 values directly. Policy evaluates filters on raw JSON (DD-11), so no V1 name is needed |
| DD-9 | The requested task is sent as the V2 event carries it, without logs or results. Results of all tasks reach the policy through `activity.tasks` (DD-10). *Corrected in revision 7.* | The V2 events do not carry task logs or results. The mappers strip them |
| DD-10 | The adapter enriches the policy input from the core `ActivityService`. An activity request adds `otherActivities`. A task request adds the tasks of the activity, with results and without logs, to `activity`. A missing activity is a defect. *New in revision 7.* | Policies need the sibling activities and the task results, and the domain events do not carry them. Reading them in DevOps keeps the events unchanged |
| DD-11 | The Policy server evaluates filtering expressions on the raw JSON input, with a lenient map accessor and `SimpleEvaluationContext`. The V1 typed conversion and `EventTypeObjectConverterUtils` are removed. *New in revision 7. Change in `odm-platform`.* | The conversion dropped V2 fields and rejected V2 values. It mutated the shared JSON mapper. The default evaluation context allowed `T(...)` |

## Original Business Requirement

When Policy is turned on at version 1, DevOps V2 must be able to ask Policy whether an activity execution and a task execution may start. Policy V2 does not exist yet. This story must not wait for it. Its DevOps side does not require a change to the Policy service, except for the filtering change of DD-11, which is made in the same story.

DevOps already emits `ACTIVITY_EXECUTION_REQUESTED` and `ACTIVITY_TASK_EXECUTION_REQUESTED`. While Policy is inactive, DevOps answers both with an approval. While Policy is active at version 1, a temporary adapter sends each request to Policy V1 and emits the V2 approval or rejection the current use cases already apply. The use cases stay the only writers of status.

Policy V1 selects policies by an event name. The names available for DevOps are `ACTIVITY_STAGE_TRANSITION`, `TASK_EXECUTION_RESULT`, and `ACTIVITY_EXECUTION_RESULT`. `currentState` and `afterState` on that request are untyped JSON. This story uses the two result names as the names of the two V2 requests, and puts the policy input contract in `afterState` (DD-2). New policies are written against that contract. Old policies written against the V1 lifecycle, stage, and descriptor are not kept working. When Policy V2 can build the policy input itself, this adapter is deleted.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Activity:** one run of a named activity on a data product version. The V2 request carries its uuid, data product version uuid, fully qualified name, version tag, name, sort order, status, and tasks.
- **Task:** a unit of work inside an activity. The V2 task request carries the activity without its tasks, and the task as the V2 event carries it (without logs or results).
- **Execution request:** the gate before work starts. Execute Activity emits activity execution requested while the activity is `PENDING`. Advance Activity emits task execution requested while the next full-control task is `PENDING` and the activity is `RUNNING`. The instrumented path uses the same two requests.
- **Execution decision:** activity execution approved or rejected, and task execution approved or rejected. The approve use cases start a `PENDING` activity or task. The reject use cases fail a `PENDING` activity or task. A decision that arrives after that status has moved is ignored.
- **Auto-approval:** while Policy is inactive or unset, DevOps turns each request into the matching approval. The approval carries the identifiers the use case reads.
- **Policy V1 evaluation request:** `POST /api/v1/pp/policy/validation`. Policy selects policies by `event`. `currentState` and `afterState` are JSON. The engine receives `{ currentState, afterState }` as sent. Policy does not check that JSON against a schema at the controller.
- **V1 result names:** `ACTIVITY_EXECUTION_RESULT` and `TASK_EXECUTION_RESULT`. In DevOps V1 these names were used after a result existed, and the document was placed in `currentState`. This story reuses the names at request time and places the document in `afterState`.
- **Policy mode:** DevOps has the inactive switch, the observer, and subscription by handler presence. It has the Policy version, address, and client. With Policy active and no version 1, the Registry loads neither its adapter nor auto-approval, and the request is never answered.
- **Activity service (core):** `ActivityService` reads activities. The adapter uses `findAllResourcesFiltered` for the sibling activities and `findOneResource` for the tasks of an activity.

### New Concepts Required

- **Policy V1 adapter:** a compatibility package, present only when Policy is active at version 1. It subscribes to the two execution requests, builds the policy input, calls Policy, and emits the V2 decision. Core use cases do not depend on it. The package is deleted when Policy V2 exists.
- **Policy input enricher (`PolicyV1InputEnricher`):** a package-private class in the adapter. It adds `otherActivities` and `activity.tasks` to the V2 event content (DD-10).
- **Lenient map accessor (Policy server):** the property accessor that lets a filtering expression read keys of a JSON object (DD-11).

### Key Business Rules

- **One answerer per request.** A request is auto-approved, or this adapter evaluates it. If Policy is active at any version other than 1, nobody answers, and the activity or task stays where it is.
- **The use case applies the decision.** The adapter emits a V2 approval or rejection. It does not start, fail, or advance the work itself.
- **Reject only a failed blocking policy.** Empty results approve. A non-blocking failure approves. A failure to reach Policy emits a rejection.
- **The policy input is built from the V2 event content.** The V2 field names and V2 status values are kept. The adapter adds the context a policy needs (DD-10) and changes no existing field. `currentState` is omitted.
- **The context is the same data DevOps holds.** `otherActivities` lists the other activities of the same data product version, and `activity.tasks` lists the tasks of the activity with their results. No other version and no descriptor is read.
- **Both execution modes share the gates.** Full control and the instrumented path both pass through the same two requests.

## Strategic Approach

### Solution Direction

Add one compatibility package on the notification bus, the way the Registry package does.

Execute Activity and Advance Activity keep emitting the two V2 requests. While Policy is inactive, the existing auto-approvers still emit the approvals. While Policy is active at version 1, the adapter is the subscriber instead. For each request it builds the policy input (DD-2, DD-10), calls Policy V1 with the result name below, and emits the V2 approval or rejection the existing handlers already accept.

The adapter is registered as a whole by the two properties, so it cannot coexist with auto-approval. Subscription stays derived from which handlers exist. Core code does not import the package.

The Policy server reads the input as raw JSON, so filters on the two names use the V2 field names (DD-8, DD-11).

### Event Map

| V2 event in | Policy V1 `event` | `afterState` (policy input) | V2 event out |
|-------------|-------------------|-----------------------------|--------------|
| `ACTIVITY_EXECUTION_REQUESTED` | `ACTIVITY_EXECUTION_RESULT` | `{ activity, otherActivities }`. `activity` is the event's activity with its tasks as the event carries them. `otherActivities` is the other activities of the version, as summaries | `ACTIVITY_EXECUTION_APPROVED` or `ACTIVITY_EXECUTION_REJECTED` |
| `ACTIVITY_TASK_EXECUTION_REQUESTED` | `TASK_EXECUTION_RESULT` | `{ activity, task }`. `activity` is the event's activity with a `tasks` list added (all tasks, with results, without logs). `task` is the requested task as the event carries it | `ACTIVITY_TASK_EXECUTION_APPROVED` or `ACTIVITY_TASK_EXECUTION_REJECTED` |

`currentState` is omitted on both. `dataProductId` is the fqn-derived id, and `dataProductVersion` is the version tag (DD-7). The V2 names and the V2 status values inside the policy input are kept (DD-8).

The decision emitted back is the approval or rejection DevOps already emits when it auto-approves. Activity uuid, name, sort order, and data product version uuid. For a task, also the task uuid and name. The use case reads the uuids.

`ACTIVITY_STAGE_TRANSITION` is not sent. Activity succeeded, failed, and canceled are not sent. The six V1 notification broadcasts are not sent.

### Key Design Decisions

- **DD-1. The two V2 requests use the two V1 result names.** `ACTIVITY_EXECUTION_REQUESTED` is sent as `ACTIVITY_EXECUTION_RESULT`. `ACTIVITY_TASK_EXECUTION_REQUESTED` is sent as `TASK_EXECUTION_RESULT`. The result name is used at request time, while the activity or task is still `PENDING`, so the existing rejection can still stop it. This is how DevOps V2 is validated before Policy V2 exists. The adapter is deleted after that upgrade.
- **DD-2. `afterState` is the policy input contract (changed in revision 7).**
  - *Problem.* Revision 3 sent the V2 `eventContent` as is. A policy then depends on the shape of the domain event. That shape is designed for the bus, not for policies, and it does not carry the sibling activities or the task results a policy needs.
  - *Decision.* `afterState` is built by the adapter from the V2 `eventContent`. For an activity it is `{ activity, otherActivities }`. For a task it is `{ activity, task }`, where `activity` carries `tasks` (DD-10). The domain events are unchanged. The policy contract is separate from them, so the events do not restrict what policies can check. Policy V2 is expected to build the same contract itself, from the domain event plus DevOps data, so policies written now survive the upgrade.
  - *Unchanged.* `currentState` is omitted. The task-result enricher in Policy reads `currentState` and would call DevOps V1 if a task were placed there. Leaving `currentState` empty skips that call.
- **DD-3. No renaming, no Registry read.** The activity name is not copied to `stage`. The V2 names and the V2 status values stay in the policy input (DD-8, DD-9). The descriptor is not loaded. Blueprint's Registry read recovered fields an old policy required. This story does not serve those old documents. The two top-level product identifiers are the exception, see DD-7.
- **DD-4. Decide on the bus.** The adapter emits the V2 decision and does not call the use case. Auto-approval stays on only while Policy is inactive. Version 1 loads this adapter for both requests. Any other version answers nothing, and the work stays pending.
- **DD-5. One package, core does not depend on it.** Policy wire types, the client, the input enricher, and the two request handlers live together. Handlers are created by the conditional configuration, not picked up by component scan.
- **DD-6. Judge the response the way the Registry adapter does.** A failed blocking policy rejects. Anything else approves, including an empty result list. A failure to reach Policy emits a rejection. A defect while building the decision or the policy input fails the notification instead of rejecting.
- **DD-7. Populate the Policy product identifiers, as the Registry does.** The Policy request carries `dataProductId` = `UUID.nameUUIDFromBytes(dataProductFqn.getBytes(UTF_8)).toString()`. That is the value the Registry's parser library returns from `IdentifierStrategyFactory.getDefault().getId(fqn)`, the legacy id that Policy stores and V1 clients search by. `dataProductVersion` = `dataProductVersionTag`. The Registry sets the same two fields on its version lifecycle request. The V2 uuid is not sent to Policy as a product id. It goes back only on the decision event. No dependency is added. The library's `getId` is this same JDK call, and it ignores the organization name. Checked against the 1.0.10 library: `urn:org.opendatamesh:dataproducts:sample-product` gives `a72bbe0c-18c6-30b0-a03a-5392d6a770ea` in both.
- **DD-8. Filtering expressions are supported on the two result names (flipped in revision 7). The execution status keeps its V2 names.**
  - *Problem.* Revision 5 excluded filters, because Policy converted `afterState` into the V1 Java types first. The V1 status enum does not accept the V2 values, `PENDING` and `RUNNING`, and the V1 types drop the V2 fields. A policy filter on V1 status names would break when Policy V2 replaces this adapter.
  - *Decision.* Filters run on the policy input contract, with V2 field names and V2 status values. The Policy server evaluates them on raw JSON (DD-11), so no V1 type is involved. No status value is translated. A filter may read `afterState.otherActivities` and `afterState.activity.tasks[*].results`.
  - *Rejected.* Mapping V2 statuses to V1 names (`PENDING` → `PLANNED`, `RUNNING` → `PROCESSING`, `SUCCEEDED` → `PROCESSED`, `FAILED` → `FAILED`, `CANCELED` → `ABORTED`). It would make the V1 status names part of the policy contract, and those policies would break after the upgrade. Sending `null` on the nested activity and task: a status filter would silently match nothing, and the status is still needed by the engine. Dropping `status` from `afterState`: policies need the status.
  - *Consequence.* Filters on these names need the Policy server change (DD-11) deployed first. On a Policy server without it, the V1 conversion rejects the V2 content with a 400, and the adapter rejects the request (fail closed).
- **DD-9. The requested task is sent as the event carries it (corrected in revision 7).**
  - *Problem.* Revision 5 said the task keeps its `results` and `logs`. That was wrong. The V2 events carry neither: the mappers `toEventRes` and `toResWithoutLogsAndResults` strip them. The results of the task's siblings and of the task itself were therefore not visible to a policy at all.
  - *Decision.* The requested `task` is sent as the V2 event carries it, without logs and without results. The results of all tasks of the activity reach the policy through `activity.tasks`, with their results and without logs (DD-10). The requested task's entry in `activity.tasks` has the same fields and an empty `results` list, because it has not run yet.
  - *Rejected.* Removing `results` and `logs` from `activity.tasks`. Those results are what a policy needs (for example, "publish needs a build result"). Logs stay out, because they are bulk data that policies do not need.
- **DD-10. The adapter enriches the policy input from the core `ActivityService` (new in revision 7).**
  - *Activity request.* `otherActivities` is the list of the other activities with the same `dataProductVersionUuid`, excluding the requested activity by uuid. Each summary has exactly `uuid`, `name`, `sortOrder`, `status`, `startedAt`, and `finishedAt`. The list is sorted by `sortOrder`, ascending, nulls last. It is `[]` when there are none. The key is always present.
  - *Task request.* `activity.tasks` holds all tasks of the activity, including the requested one. Each task keeps its fields. Its `logs` is removed, and its `results` is a list of `uuid`, `content`, and `generatedAt`. The list is sorted by `sortOrder`, nulls last. The requested `task` is not changed by this rule (DD-9).
  - *Reads.* `findAllResourcesFiltered` with `ActivitySearchOptions.dataProductVersionUuid` and an unpaged request, for the siblings. `findOneResource` for the activity of a task request. Both return data in a transaction, so the adapter reads no lazy relation.
  - *Dates.* Every date (`startedAt`, `finishedAt`, `generatedAt`) is an ISO-8601 text with milliseconds and an offset, for example `2026-10-15T15:19:00.123+00:00`. The dates are written by the same Jackson mapper configuration as the events, so no epoch number is mixed in.
  - *No descriptor, no cross-version context, no size limit.* Decided for now. Other versions of the data product are not read. Results are sent in full.
  - *Missing activity.* A missing activity (or a missing version uuid or fqn on an activity request) is a defect. The exception propagates, the notification fails, and no decision is emitted. The activity stays `PENDING` until the notification is handled. Rejection would hide a defect in DevOps as a policy refusal.
- **DD-11. The Policy server evaluates filters on raw JSON (new in revision 7; change in `odm-platform`).**
  - *Problem.* `SpELUtils` converted the input into the V1 Java types (`EventTypeObjectConverterUtils`) so that SpEL could navigate with getters. The conversion had four defects. It dropped the V2 fields the V1 types do not declare, and it rejected the V2 status values, so a valid V2 document failed with a 400 (`SC400_05_MALFORMED_INPUT_OBJECT`). It called `configure(FAIL_ON_UNKNOWN_PROPERTIES, true)` on the shared static `JSON_MAPPER`, so the whole process changed its JSON behaviour after the first filtered evaluation. The default `StandardEvaluationContext` allowed `T(...)` type references, so a policy definition could run code on the Policy server.
  - *Decision.* The input is converted to plain Java values (`Map`, `List`, `String`, `Number`, `Boolean`, `null`) with a mapper that is not configured. Expressions are evaluated with `SimpleEvaluationContext.forPropertyAccessors(lenient map accessor).withInstanceMethods()`. The lenient accessor reads any key of a `Map`. A missing key reads as `null`. The accessor is read-only. An evaluation error or a result that is not a boolean filters the policy out. The four V1 classes and `EventTypeObjectConverterUtils` are deleted. The `eventType` argument of `eventObjectMatchesSpelExpression` is kept in the signature, so `ValidationService` does not change. It is used only in log messages.
  - *Constraints documented in code.* `SpELUtils` states that expressions run on the raw JSON whatever its shape, that they use JSON field names, that a missing key reads as `null`, that `SimpleEvaluationContext` is deliberate (no `T(...)`, constructors, or bean references), and that the getters of the old V1 classes are not available. Methods of the JSON value classes (String, List, Map, Number, Boolean) remain callable.
  - *Rejected.* Keeping the typed conversion and adding a JSON fallback. A partial conversion silently drops V2 fields, so the policy result would depend on which path ran. Keeping `StandardEvaluationContext`, because it runs code from policy definitions.
  - *Consequence.* Filters that use `T(...)`, constructors, or the V1 getters stop working. Such a filter is a policy written against the V1 documents, which this story does not support.

### Alternatives Considered

- **Map `ACTIVITY_EXECUTION_REQUESTED` to `ACTIVITY_STAGE_TRANSITION`, and do not send the result names.** This was revision 1. Rejected. It leaves the task request without a Policy name, and it forces the V2 activity into the lifecycle document.
- **Map `TASK_EXECUTION_RESULT` only after the task has a result, and `ACTIVITY_EXECUTION_RESULT` only after the activity has succeeded.** Rejected. Those moments have no V2 request, and rejection no longer applies once the status has left `PENDING`. The use cases stay V2, so no new request is added there.
- **Auto-approve the task request and evaluate only the activity request.** Rejected with DD-1. Both requests are evaluated.
- **Rename V2 fields to the V1 names (`stage`, `dataProductId`, `template`).** Rejected with DD-2 and DD-3. New policies are written against the policy input, which keeps the V2 names.
- **Call Registry to rebuild a descriptor or a lifecycle.** Rejected with DD-3, and the descriptor is still not loaded (revision 7 keeps this). The document does not need them.
- **Add the DPDS parser library to compute the id.** Rejected with DD-7. The library's `getId` is a single JDK call, and the adapter makes the same call.
- **Map the V2 statuses to V1 names, so that filtering expressions work.** Rejected with DD-8. Policies would depend on V1 names, which break when Policy V2 replaces the adapter.
- **Set the execution status to null on the nested activity and task.** Rejected with DD-8.
- **Drop `status` from `afterState`.** Not chosen. Policies need the status, and DD-2 keeps the V2 content whole.
- **Call Policy from inside the approve use cases.** Rejected. The decision re-enters on the bus, as in the Registry.
- **Send the other tasks in a separate `otherTasks` list next to the requested task (revision 7).** Rejected in favour of `activity.tasks`. The tasks belong to the activity, and one list keeps the shape of the activity the same in both requests.
- **Add the descriptor to the policy input (revision 7).** Rejected for now. It needs a Registry read on every request and a second source of truth. Decide it when a policy needs it.
- **Read the other versions of the data product (revision 7).** Deferred. Only the same version is read, as DD-10 says. A policy that needs the previous version needs its own design.
- **Enrich the domain events in the producer (revision 7).** Rejected. It would couple the domain events to the needs of one policy engine, and it would add the same data to every consumer of the event. Research report section 4 gives the reasons for enriching at the consumer boundary.
- **Keep `StandardEvaluationContext` for filters (revision 7).** Rejected with DD-11. It allows `T(...)` and therefore code execution from a policy definition.
- **Typed conversion first, JSON fallback for the rest (revision 7).** Rejected with DD-11. A partial conversion silently drops V2 fields, so the result would depend on the input.

## Known Limitations

These are accepted trade-offs. Each one is removed when Policy V2 replaces the adapter, unless the text says otherwise.

- **Filters on these names need the Policy change (DD-8, DD-11).** On a Policy server without DD-11, a policy with a filter on `ACTIVITY_EXECUTION_RESULT` or `TASK_EXECUTION_RESULT` gets a 400 from the V1 conversion, and the adapter rejects the request. This is fail closed, but it blocks the activity. Deploy the Policy change before a filter is written.
- **Filters can no longer use the V1 classes or `T(...)`.** Filters read JSON. They cannot call the getters of the old V1 types, cannot use `T(...)`, and cannot call constructors (DD-11). A filter written against those is broken, and it is not supported.
- **Methods of JSON values are callable.** Methods of String, List, Map, Number, and Boolean values, for example `startsWith`, are available to filters. `T(...)`, constructors, and bean references are not. `getClass()` was checked and is rejected. This is a restriction, not a sandbox, so keep policy definitions under the same control as the rest of Policy.
- **No lock on the context.** The activity context is read when the request is handled. Two requests that arrive together can both pass a check such as "no other activity is `RUNNING`", because neither sees the other's status change. The use cases still apply the status, so this is a race on the policy decision.
- **No size limit on results.** `activity.tasks[*].results[*].content` is sent in full with every task request, and Policy stores the whole input of every evaluation (`storeResults=true`).
- **Only the same data product version is read.** Other versions of the data product and the descriptor are not in the policy input (DD-10).
- **Policies read V2 names and V2 values.** A policy written for these two names reads `sortOrder`, `name`, `dataProductFqn`, and the V2 status values. Policies written against the V1 documents for these names do not match the V2 content, and this story does not support them.
- **Malformed input is no longer a 400.** The raw JSON is not converted, so no input shape fails the conversion. A missing key reads as `null`. An expression that errors filters the policy out, as before.
- **DevOps has no Policy configuration yet.** There is no version, address, or active property for Policy. They are required before the adapter can load.
- **Decisions need Notification.** With Notification inactive, the decision is never delivered, and the activity or task stays `PENDING`.
- **A missing activity leaves the work pending.** It is a defect (DD-10), so the notification fails and no decision is emitted. Nothing retries it in this story.

## Risk & Gap Analysis

### Requirement Ambiguities

Resolved in revisions 3 to 7.

| Former gap | Resolution |
|------------|------------|
| Which V1 names are in scope | DD-1. The two result names. `ACTIVITY_STAGE_TRANSITION` and the notification broadcasts are out. |
| Jira acceptance criteria were unknown | The goal is the one in the requirement above. The unread ticket text no longer chooses the mapping. |
| What the task request should say to Policy | DD-1. `TASK_EXECUTION_RESULT`, not an automatic approval. |
| Whether `currentState` must carry the previous lifecycle | DD-2. `currentState` is omitted. |
| Whether to read the descriptor from Registry | DD-3. No. |
| Whether the activity name is the stage, the tag is the version, and the id is the fqn | The name is not the stage (DD-3). The tag is the version, and the fqn-derived id is the product id (DD-7). |
| Policy active at a version other than 1 | DD-4. Same as the Registry. Nobody answers, and the work stays pending until that version has a handler. |
| Whether a filtering expression can be used on the two result names | DD-8, revision 7. Yes, against the policy input, once DD-11 is deployed. |
| Whether the policy sees the other activities and the task results | DD-10, revision 7. Yes. Other activities of the same version, and the task results of the activity. |
| Whether the domain events must change for the policy | DD-2, revision 7. No. The policy input is built in the adapter. |

No requirement ambiguity is still open.

### Edge Cases

- **Empty Policy results, and a failed policy that is not blocking.** Both approve.
- **Policy cannot be reached.** The adapter emits the rejection. The pending activity or task fails through the existing use case.
- **A second decision after the status has moved.** The approve and reject use cases do nothing.
- **Instrumented and full control.** Both emit the same two requests, so both are evaluated.
- **First activity of a version.** `currentState` is omitted, as it is for every other activity. `otherActivities` is `[]`.
- **Activity without a sort order.** It is listed last in `otherActivities` and in `activity.tasks`.
- **Activity or task not found.** A defect. The notification fails, no decision is emitted (DD-10).
- **A task with no results yet.** Its `results` is `[]`.
- **A V2 status outside the five values.** Not expected. It reaches the policy unchanged, because no status is translated (DD-8).
- **A filter that returns a non-boolean or null, or errors.** The policy is filtered out (DD-11). The result is not an error for the request.
- **A filter on a Policy server without DD-11.** A 400 from the V1 conversion, and the adapter rejects (fail closed).

### Technical Risks

- **Two handlers for one request.** The adapter and auto-approval must not both be present. The version property is part of that condition, and DevOps configuration does not have it yet.
- **Notification inactive.** The decision is never delivered and the activity stays pending.
- **Id encoding.** The adapter pins UTF-8. The library uses the platform default. The unit test vectors catch a change (DD-7).
- **Deployment order.** DevOps sends `otherActivities` and `activity.tasks` with every request. A Policy server without DD-11 still stores them, but any filter on these names fails there. Deploy the Policy change first, then the DevOps change, or accept the fail-closed rejections in between.
- **Cost per request.** An activity request reads all activities of the version. A task request reads the activity with its tasks and results. Both are small in practice. The results are not bounded (see Known Limitations).
- **Filters that rely on the removed V1 behaviour.** Nothing lists the existing policies that filter on these names. Review them before the Policy change is released (see Open Items).

### Open Items for REASONS

1. Add the Policy configuration to DevOps: active, version, and address. Keep the version check that makes the adapter load only at version 1.
2. Done in revision 7: the adapter is tested through the observer, with the Policy client mocked. The tests assert the evaluation request (event name, `dataProductId`, `dataProductVersion`, the policy input with `otherActivities` or `activity.tasks`, `currentState` omitted) and the emitted decision.
3. Done: the id is tested with the vectors in DD-7.
4. Before the Policy change is released, list every non-empty `filtering_expression` stored in each Policy database, for every event, not only the two result names. DD-11 changes how all filters are evaluated, including the Registry's `DATA_PRODUCT_*` events. Review each one against the new semantics: JSON field names, no `T(...)`, no V1 getters.
5. Done: with Policy inactive, both requests are auto-approved (AC 1). The test is `PolicyV1AdapterInactiveIT`.
6. Run the live end-to-end test again (see `agent-reports/devops-policy-v1-adapter-e2e-test.md`, update of 2026-10-15). It covers a filter on `otherActivities` and on `activity.tasks[*].results`, and the 400 path on a Policy server without DD-11.
7. Revisit a size limit on results if stored evaluations grow too large. No limit is decided for now. Decide whether the "no other activity RUNNING" class of rule needs a lock. That is not decided.

### Acceptance Criteria Coverage

No acceptance criteria were available from Jira. The rows are the outcomes of revision 3. DD-7 to DD-9 were added in revisions 5 and 6. DD-10 and DD-11 were added in revision 7.

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | With Policy inactive, both requests are still auto-approved | Yes | The adapter does not load in this mode. |
| 2 | With Policy active at version 1, both requests are evaluated and answered with the existing approval or rejection | Yes | DD-1 and DD-4. |
| 3 | The Policy document is the policy input built from the V2 event content, under a V1 result name | Yes | DD-2, DD-8, DD-9, and DD-10. The domain events are unchanged. |
| 4 | Use cases stay free of Policy and remain the only writers of status | Yes | DD-4. |
| 5 | A blocking failure prevents the pending activity or task from starting | Yes | The check runs on the request, while the status is still `PENDING`. |
| 6 | Old V1 policy documents keep working without a rewrite | No | Out of scope. DD-3. |
| 7 | A policy can check the other activities of the version and the results of the task's activity | Yes | DD-10. Other versions are not read. |
| 8 | A policy can filter on the V2 field names and values of the two result names | Yes, after DD-11 is deployed | DD-8 and DD-11. On an older Policy server the filter fails closed. |
