# SPDD Analysis: Rename `dataProductVersionTag` to `dataProductVersionNumber`

Story **BDMD-5647** (epic **BDMD-5097**). It corrects a naming decision from **BDMD-5436** (Activity and Task CRUD), which BDMD-5437, BDMD-5438, BDMD-5442 and BDMD-5443 then carried forward. The change crosses repositories: this document also plans the changes in `odm-cli`, `blindata-ui`, `blindata-agent` and `odm-platform-adapter-observer-blindata`.

**Revision 2 (2026-11-04).** Applies the team review of revision 1.
- All repositories are released together, so there is no transition layer and no JSON alias. The revision 1 DD-69 (input compatibility) and DD-70 (event readers accept both names) are withdrawn.
- DD-69 now records the joint release. Later decisions are renumbered.
- Earlier analyses and prompts are corrected in place, not given a pointer line, so each stays the source of truth (DD-72).
- Q1–Q5 are closed: see [Closed in revision 2](#closed-in-revision-2).

**Revision 1 (2026-11-04).** First pass. The team approved the rename. DevOps is already deployed to `dev`, so the column is renamed by a new Flyway migration and `V1__init_schema.sql` stays unchanged (DD-67). The observer feature BDMD-5558 already reads this field and is in scope.

## Original Business Requirement

> We should change `dataProductVersionTag` to `dataProductVersion` in `ActivityRes`, as that would be more accurate and less confusing.
>
> 1. We can't just do the in-place change to the V1 migration. DevOps has already been deployed to `dev`, so we must use a migration to change the column name, as well as the entity and the resource name, to avoid breaking the DB.
> 2. We just implemented a new feature in the Blindata observer (BDMD-5558). We might have to update that as well.
>
> Write down the problem and the fixes planned for all the other repos as well (UI, CLI, observer etc.).

The requirement says `dataProductVersion`. This analysis uses **`dataProductVersionNumber`** instead (DD-66).

## The Problem

### Three version identifiers

| Where | Field | Value | Example |
|---|---|---|---|
| Descriptor | `info.version` | The data product version | `1.2.0` |
| Registry V2 | `versionNumber` | `info.version`. Extracted at publish (`DataProductVersionPublisher`, `extractVersionNumber`). Required, and unique per data product. | `1.2.0` |
| Registry V2 | `tag` | The release's **Git tag**. Optional. The UI uses it as the executor `ref`. | `v1.2.0` |
| Registry V2, delete event | `dataProductVersionNumber` and `dataProductVersionTag` | Both are sent: `versionNumber` and the Git `tag` (`DataProductVersionDeleterNotificationOutboundPortImpl`) | `1.2.0`, `v1.2.0` |
| **DevOps 2.0** | **`dataProductVersionTag`** | **`info.version`**, despite the name | `1.2.0` |

### How the meaning drifted

| Story | What "version tag" means there |
|---|---|
| BDMD-5436 (DevOps CRUD) | Registry's Git `tag`. "The same three fields as the Registry version event" (line 41). "A version tag can be absent in Registry" (line 56), which is only true of `tag`. |
| BDMD-5437 (full control) | Still the Git tag: "the ref (the version tag), from `DataProductVersionRes.tag`" (lines 127, 364). |
| BDMD-5438 (Policy V1 adapter), DD-7 | Sends `dataProductVersion = dataProductVersionTag` "as the Registry does". Registry sends `versionNumber`, so this silently assumes the field holds the version number. |
| BDMD-5518 (UI) | Fills it with `release.content.info.version` (`ExecuteDevOpsV2ActivityFlow.js:82`). |
| BDMD-5443 (CLI) | Fills it with `--data-product-version` (`info.version`). Its analysis notes at line 133: "DevOps' version tag is Registry's `versionNumber`, not Registry's `tag`." |
| BDMD-5558 (observer) | Maps the JSON name `dataProductVersionTag` to a Java field `dataProductVersionNumber`, with a comment calling the name misleading (`OdmActivityResourceV2.java:15-20`). |

The value every client sends, and that Policy and the observer depend on, is the **version number**. Only the name is wrong.

### Why it matters

- **The name points to the wrong value.** The Swagger text says "Data product version tag". Registry already has a `dataProductVersionTag` that holds the Git tag. A client that follows the name sends `v1.2.0` instead of `1.2.0`.
- **What breaks when that happens:**
  - Request Task Execution, Record Task Status, Record Task Logs and Record Task Results find the open activity by FQN + version + activity name (`findOpenActivities`). The CLI looks up by `info.version`, finds nothing, and `start-task` fails.
    - Execute Activity's concurrency guard checks DPV UUID + name (`ExecuteActivity.refuseIfAnotherExecutionIsOpen`), so the CLI also can't create a second activity.
  - Policy V1 receives the Git tag as `dataProductVersion`. The observer looks up evaluation results by `info.version` and finds none.
- **Every BDMD-5558 reader repeats the explanation.** The observer analysis needs a table and DD-11 just to say that the "tag" is not a tag.
- **The cost grows after release.** The field is in the REST API, in every DevOps V2 event payload, and in the Policy V1 `afterState`. Once external clients read it, renaming it is a breaking change.

### Current state

- DevOps runs in `dev`. Its database has the `data_product_version_tag` column (Flyway `V1__init_schema.sql`), and its rows must be kept.
- `V1__init_schema.sql` is identical on `origin/main`, `origin/BDMD-5438-…` and `origin/BDMD-5443-…` (same MD5). No branch adds a `V2` migration, so `V2` is free.
- The UI's DevOps V2 flow is on `blindata-ui` `master` (#404).
- The agent's `OdmDevOpsActivityV2Res` is on `blindata-agent` `master` (#50).
- The CLI's DevOps V2 commands exist only on `odm-cli` `feat/java21-spring-boot-3`. They are not merged, and no CLI build is published.
- The observer's BDMD-5558 work is uncommitted on its branch `BDMD-5558-odm-blindata-observer-gestione-nuovi-eventi-dev-ops-2-0`.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Activity natural key:** data product FQN + version + activity name. The four task use cases resolve the open activity with it (`findOpenActivities`). The CLI addresses an activity by the same key (`DevOpsActivityKey`).
- **Activity identity for concurrency:** DPV UUID + activity name (`ExecuteActivity.refuseIfAnotherExecutionIsOpen`). It does not use the version field.
- **Registry DPV reference:** `dataProductVersionUuid` (identity), `dataProductFqn`, and the version field (BDMD-5436).
- **Git ref:** stays per task, in `executorParameters.ref.name`. With additional repositories, one activity has several refs. DevOps does not need an activity-level Git tag.
- **Event content:** `ActivityMapper.toEventRes` returns an `ActivityRes`, so every DevOps V2 activity event carries the same field names as the REST resource.
- **Policy V1 input:** `PolicyV1InputEnricher` builds `afterState` from the event content and from `ActivityRes` siblings. The Policy request's own `dataProductVersion` field is filled from the received event (`NotificationEventHandlerActivity*ExecutionRequested.java:78,81`).

### Key Business Rules

- The activity's version is the descriptor's `info.version`, which is Registry V2's `versionNumber`. It is never the Git tag.
- The value is unchanged: only the name changes. No data is rewritten.
- Registry's `dataProductVersionTag` (delete event) is the Git tag and keeps its name.

## Strategic Approach

### Solution Direction

Rename the field everywhere it means the version number: API, events, Policy input, entity, column, use cases, CLI client, UI and observer. A Flyway `V2` migration renames the column in place. Every repository is released together, so the old name disappears in one step, with no alias and no transition period.

### Key Design Decisions

- **DD-66: The new name is `dataProductVersionNumber`.**
  - It matches Registry V2's `versionNumber` and Registry's delete-event field `dataProductVersionNumber`. Those already hold the same value.
  - `dataProductVersion` (the name in the requirement) is rejected. In Registry V2 that name means the whole version object (`EventContent.dataProductVersion` is a `DataProductVersionRes`). Policy V1's string `dataProductVersion` is a legacy API, and the adapter already maps to it.
  - Column: `data_product_version_number`. Java: `dataProductVersionNumber` / `getDataProductVersionNumber()`.

- **DD-67: The column is renamed by a new Flyway migration, `V2__rename_activities_data_product_version_tag.sql`.**
  - `dev` has already applied `V1`. Editing it changes its checksum, and Flyway's validate-on-migrate refuses to start the service. `ddl-auto: validate` would also fail on the entity–column mismatch.
  - The migration (PostgreSQL; unqualified, like `V1`):
    ```sql
    ALTER TABLE activities RENAME COLUMN data_product_version_tag TO data_product_version_number;
    ```
  - In PostgreSQL this changes only the catalog: it keeps the rows and doesn't rewrite the table. There is no index or constraint on the column to rename.
  - `V1__init_schema.sql` is not touched.
  - The column stays nullable, like the other activity columns.

- **DD-68: DevOps renames everything internal as well.**
  - Entity, metamodel, repository spec, CRUD validation, use-case commands, ports, adapters and the policy adapter.
  - The use-case error messages say "version" instead of "version tag".
  - The Swagger text describes the field as "the descriptor's `info.version` (Registry V2 `versionNumber`); not the Git tag".
  - The sort property becomes `dataProductVersionNumber` (`ActivityController.java:78,93`).

- **DD-69: One joint release, no compatibility layer.**
  - DevOps, the CLI, the UI, the agent and the observer are released together. No client is ever deployed against a DevOps that uses the other name.
  - No `@JsonAlias`, no deprecated setter, and no reader that accepts both names, in DevOps or in the observer.
  - Events emitted under the old name before the release are not read again. Events that the notification server already stored keep the old name as history.

- **DD-70: The version becomes required on Execute Activity.**
  - Every client already sends it, and the four lookup use cases and the Policy request already treat a blank value as an error.
  - BDMD-5436's reason for making it optional ("a version tag can be absent in Registry") applies to the Git tag, not to `versionNumber`, which Registry requires.
  - `ExecuteActivity` validation adds "Data product version number is required", next to the UUID and name checks (`ExecuteActivity.java:96-100`).
  - This is API validation only. The column stays nullable (DD-67).

- **DD-71: Registry, the Git tag and the executors don't change.**
  - Registry's `tag` and its delete event's `dataProductVersionTag` really are the Git tag. `odm-platform` `DataProductVersionDeletedEventContent` mirrors that event and is correct.
  - The Git tag stays per task in `executorParameters.ref.name`. No activity-level Git tag is added.
  - Executors never receive the activity version.

- **DD-72: Earlier analyses are corrected in place.**
  - Each analysis must stay the source of truth. A pointer line would leave the old name and the old meaning in the body, and readers would have to reconcile the two.
  - Every analysis that uses `dataProductVersionTag`, `data_product_version_tag`, or "version tag" for this field is edited to use `dataProductVersionNumber` / `data_product_version_number` / "version number", and to say the value is the descriptor's `info.version` (Registry V2 `versionNumber`).
  - Where an analysis says "version tag" but means the **Git tag** (the executor `ref`), it now says "Git tag", so the two terms no longer overlap.
  - The edit changes wording, not decisions. Revision histories stay as they are. No new revision entry is added to the corrected analyses.
  - The files and the passages to fix are listed per repository below.
  - Out of scope: analyses where "version tag" means something else. These are `blindata-ui` BDMD-5154 (Git version tag of blueprints) and `odm-cli` BDMD-5443 `cli-docker-image` (Docker image tag).
  - SPDD prompts are corrected in place the same way, so that a prompt never disagrees with its analysis or with the code: DevOps BDMD-5436, 5437, 5438, 5442 and 5443; CLI BDMD-5443; UI BDMD-5518; agent BDMD-5518; observer BDMD-5558. The prompt for this story is the spec for the renamed code.
  - Other living documents are updated as well: this service's `old/README.md` and the observer's `docs/configuration/event-handling.md`.

### Changes per repository

#### `odm-platform-pp-devops-server` (this repository)

**Database**

| File | Change |
|---|---|
| `src/main/resources/db/migration/postgresql/V2__rename_activities_data_product_version_tag.sql` | **New.** The `ALTER TABLE … RENAME COLUMN` from DD-67. |
| `V1__init_schema.sql` | No change. |

**Main code** (paths under `src/main/java/org/opendatamesh/platform/pp/devops/`)

| Area | Files | Change |
|---|---|---|
| Entity | `activity/entities/Activity.java` | Field, getter, setter; `@Column(name = "data_product_version_number")`. `Activity_` is regenerated. |
| Repository | `activity/repositories/ActivitiesRepository.java` | `Specs.hasDataProductVersionTag` → `hasDataProductVersionNumber`. |
| CRUD service | `activity/services/core/ActivityServiceImpl.java` | Filter (line 88), trim (line 110), 255-length check and message (line 128). |
| Execute Activity | `activity/services/usecases/execute/ExecuteActivity.java` | New required check (DD-70). |
| Use-case wiring | `activity/services/ActivityUseCasesService.java` | The four command constructions (lines 115, 131, 148, 164). |
| Request Task Execution | `RequestTaskExecution`, `…Command`, `…PersistenceOutboundPort`, `…PersistenceOutboundPortImpl` | Command component, port parameter, search option setter, error messages. |
| Record Task Status | `RecordTaskStatus`, `…Command`, `…PersistenceOutboundPort`, `…PersistenceOutboundPortImpl` | Same. |
| Record Task Logs | `RecordTaskLogs`, `…Command`, `…PersistenceOutboundPort`, `…PersistenceOutboundPortImpl` | Same. |
| Record Task Results | `RecordTaskResults`, `…Command`, `…PersistenceOutboundPort`, `…PersistenceOutboundPortImpl` | Same. |
| REST resources | `rest/v2/resources/activity/ActivityRes.java` | Field, accessors, Swagger text. The event payloads follow, through `ActivityMapper.toEventRes`. |
| | `rest/v2/resources/activity/ActivitySearchOptions.java` | Field and accessors; the query parameter becomes `dataProductVersionNumber`. |
| | `rest/v2/resources/activity/usecases/task/ActivityTaskExecutionRequestCommandRes.java`, `ActivityTaskStatusCommandRes.java`, `ActivityTaskLogsCommandRes.java`, `ActivityTaskResultsCommandRes.java` | Field, accessors, Swagger text. |
| Controller | `rest/v2/controllers/ActivityController.java` | Sort-property lists in the search docs (lines 78, 93). |
| Policy V1 adapter | `old/v1/policyservice/EventReceivedActivityExecutionRequested.java` | Field and accessors. |
| | `old/v1/policyservice/NotificationEventHandlerActivityExecutionRequested.java:78`, `NotificationEventHandlerActivityTaskExecutionRequested.java:81` | Read the renamed getter. The Policy request field stays `dataProductVersion`. |
| | `old/v1/policyservice/PolicyV1InputEnricher.java` | No code change. `afterState` takes the new name from `ActivityRes`. |
| Docs | `old/README.md` | The `afterState` example (line 56). |

The MapStruct `ActivityMapper` needs no edit, since both sides are renamed.

**Tests** (paths under `src/test/java/org/opendatamesh/platform/pp/devops/`)

| File | Change |
|---|---|
| `activity/services/usecases/recordtaskstatus/RecordTaskStatusTest.java` | Rename. |
| `activity/services/usecases/recordtasklogs/RecordTaskLogsTest.java` | Rename. |
| `activity/services/usecases/recordtaskresults/RecordTaskResultsTest.java` | Rename. |
| `rest/v2/controllers/ActivityInstrumentedFlowIT.java` | Rename the body fields and the search query parameter. |
| `old/v1/policyservice/PolicyV1AdapterTestSupport.java`, `PolicyV1AdapterIT.java`, `PolicyV1AdapterInactiveIT.java` | Rename. Assert that `afterState` carries `dataProductVersionNumber` and that the Policy request's `dataProductVersion` is unchanged. |
| Execute Activity tests | Add: a missing version is refused (DD-70). |
| Migration | Covered by the Testcontainers ITs, which run `V1` then `V2` with `ddl-auto: validate`. |

**SPDD analyses corrected in place** (DD-72; paths under `spdd/analysis/`)

| File | Passages to fix |
|---|---|
| `BDMD-5436-…-[Analysis]-activity-task-anemic-crud.md` | Line 31: the event natural key is FQN + version **number**. Line 41: the three fields become `dataProductVersionUuid`, `dataProductFqn`, `dataProductVersionNumber`; the value is `info.version`; drop "the same three fields as the Registry version event". Line 45. Line 56: the version is stored with the activity, and the reason "a version tag can be absent in Registry" goes (Registry always sets `versionNumber`). Line 66: search filter. Lines 147, 172, 173: column names and wording. Line 182: drop "Registry does not always set a version tag". Line 219. Line 237 (table row 8). |
| `BDMD-5437-…-[Analysis]-full-control-happy-path.md` | Line 102: the activity carries the DPV identity (UUID, FQN, version number). Line 127: "the ref (the release's Git tag)". Line 364: "the ref: the release's Git tag, from `DataProductVersionRes.tag`". Line 371: "the Git tag". |
| `BDMD-5437-…-[Interfaces]-full-control-happy-path.md` | Line 276: "the Git tag is enough". Line 295: `"dataProductVersionNumber": "1.2.0"`. The example currently shows `"v1.2.0"`, a Git-tag value, which is the confusion this story removes. Line 465: the event example field name. |
| `BDMD-5438-…-[Analysis]-policy-v1-adapter.md` | Line 35 (DD-7 summary), line 53, line 97, line 114: `dataProductVersion` = `dataProductVersionNumber` (the descriptor's `info.version`, as Registry sends `versionNumber`). Update the `afterState` example. |
| `BDMD-5442-…-[Analysis]-instrumented-path.md` | Lines 5, 83, 103, 122, 259 (DD-52), 264 (DD-57): the natural key is "data product FQN, version number, and activity name". |

Then write the BDMD-5647 prompt from this analysis.

#### `odm-cli` (branch `feat/java21-spring-boot-3`, not merged)

The CLI is the main reader of the natural key. Paths under `src/main/java/org/opendatamesh/cli/`.

| File | Change |
|---|---|
| `clients/platform/devops/DevOpsActivityKey.java` | Record component `dataProductVersionTag` → `dataProductVersionNumber`; update the Javadoc. |
| `clients/platform/devops/DevOpsActivity.java` | Record component. |
| `clients/platform/devops/resources/DevOpsActivityRes.java` | JSON field (it's the execute body and the read response). |
| `clients/platform/devops/resources/DevOpsActivitySearchOptions.java` | The query parameter name. |
| `clients/platform/devops/resources/DevOpsTaskCommandRes.java` | JSON field of the four task command bodies. |
| `clients/platform/devops/PlatformDevOpsClient.java`, `PlatformDevOpsClientImpl.java` | Usages and debug logs (lines 61, 66, 81, 93, 118, 182, 188). |
| `usecases/devops/starttask/OpenActivity.java` | Message at line 115. |
| Tests: `PlatformDevOpsClientImplTest`, `ActivityPlanTest`, `PlatformDevOpsCommandIT` | Rename and update JSON expectations. |
| SPDD analysis `spdd/analysis/BDMD-5443-…-devops-v2-cli-commands.md` | Corrected in place (DD-72). Line 86: the natural key is "data product FQN + version number + activity name". Line 91: `dataProductVersionNumber` in the task address. Line 115: "Version number: the descriptor's `info.version`". Line 133: keep the Registry `versionNumber` / `tag` explanation, and say DevOps' `dataProductVersionNumber` is Registry's `versionNumber`; drop "DevOps' version tag". Line 344 (Q10): "The DevOps version number is the descriptor's `info.version`…". |

No change:
- The `--data-product-version` option and `StartTaskCommand.dataProductVersion()` already have the right meaning.
- The CLI matches DevOps errors only on "is already PENDING or RUNNING" (`StartTaskDevOpsOutboundPortImpl.java:18`), which this change doesn't touch.

#### `blindata-ui`

| File | Change |
|---|---|
| `src/pages/dataops/registry/versions/activities/detail/ExecuteDevOpsV2ActivityFlow.js:82` | `dataProductVersionTag:` → `dataProductVersionNumber:` |
| `…/ExecuteDevOpsV2ActivityFlow.test.js:81` | `plan.activity.dataProductVersionNumber` |

No UI analysis names this field, so there is no SPDD change. BDMD-5154's "Git version tag" is about blueprints and is out of scope (DD-72).

No screen displays the field. The `DataProductVersionTag` components in `DataProductsListPage.jsx` and `RelatedDataProductsSection.jsx` belong to Blindata data products and are unrelated.

#### `odm-platform-adapter-observer-blindata` (BDMD-5558 branch, uncommitted work)

The changes go on the current, uncommitted BDMD-5558 branch.

| File | Change |
|---|---|
| `src/main/java/…/resources/odm/devops/v2/OdmActivityResourceV2.java` | Remove `@JsonProperty("dataProductVersionTag")`. The Java field `dataProductVersionNumber` already has the JSON name. Drop the "misleading name" Javadoc. |
| `src/test/resources/…/rest/v2/*_eventNotification.json` (7 fixtures: `activitySucceeded_dev`, `activitySucceeded_prod`, `activitySucceeded_instrumented`, `activityFailed_dev`, `activityFailed_prod`, `activityCanceled`, `unknownType`) | JSON key → `dataProductVersionNumber`. |
| `src/test/java/…/services/v2/devopsevents/DevOpsActivityEventResolverTest.java` (lines 185-187, 224) | Use the new JSON name. `whenActivityParsedThenDataProductVersionTagJsonIsMappedToVersionNumber` only tested the old-name mapping, so remove it or turn it into a plain parsing test. |
| `docs/configuration/event-handling.md:142` | Drop the "misleading name" note and use the new name. |
| `spdd/analysis/BDMD-5558-…-devops-v2-events-handling.md` | Update lines 10, 160, 179, 181, 264, 292-293, 306 and 323 (Q8). In the version table (line 179), the DevOps row becomes `ActivityRes.dataProductVersionNumber`. DD-11's "misleading name" note goes. |
| `spdd/prompt/BDMD-5558-…-service-devops-v2-events-handling.md` | Same update. |

`DevOpsActivityEventResolver` (lines 69, 102) already uses `getDataProductVersionNumber()`, so its code doesn't change. The observer's V1 handling is not involved.

#### `blindata-agent`

| File | Change |
|---|---|
| `src/main/java/io/blindata/agent/services/odm/client/v2/resources/OdmDevOpsActivityV2Res.java` | Rename the field and its accessors. Nothing calls the getter, so this is consistency only. |

No agent analysis names this field, so there is no SPDD change.

The proxy forwards request and response bodies without remapping, so UI calls through it carry the new name unchanged.

#### Repositories with no change

| Repository | Why |
|---|---|
| `odm-platform-pp-registry-server` | Its `dataProductVersionTag` is the Git tag (DD-71). |
| `odm-platform` (notification API) | `DataProductVersionDeletedEventContent` mirrors Registry's delete event. |
| Notification server | Stores and dispatches event content without reading it. Events already stored keep the old name as history (DD-69). |
| Policy server | The request field `dataProductVersion` is unchanged, and no policy reads `afterState.activity.dataProductVersionTag` (Q1). |
| `odm-platform-adapter-devops-executor-*`, `odm-platform-adapter-validator-opa` | They never receive the field. |

### Release

1. Merge the DevOps, CLI, UI, agent and observer changes.
2. Release and deploy them together (DD-69). On startup, DevOps runs `V2` and renames the column.
3. Check that `grep -r dataProductVersionTag` across the repositories matches only Registry's Git-tag usages and `odm-platform`'s copy of the Registry delete event. No SPDD analysis or prompt should match.

### Alternatives Considered

- **Edit `V1__init_schema.sql` in place.** Rejected. `dev` has applied `V1`, so the checksum mismatch stops the service until each environment's `odm_devops` schema is dropped or repaired, and the `dev` activities would be lost.
- **Keep the column, rename only the API (`@Column(name = "data_product_version_tag")` on the new field).** Rejected. It avoids a migration but leaves the misleading name in the schema, and the requirement asks for the column too.
- **Rename to `dataProductVersion`.** Rejected (DD-66).
- **A transition layer (`@JsonAlias`, a deprecated search setter, event readers that accept both names).** Proposed in revision 1. Withdrawn in revision 2: all repositories are released together, so no client ever meets the other name (DD-69).
- **Make the column `NOT NULL`.** Rejected. The other activity columns are nullable, and DD-70 already enforces the value on Execute Activity.
- **A pointer line in earlier analyses instead of correcting them.** Proposed in revision 1. Withdrawn in revision 2: the body would still teach the old name and meaning, so the analysis would stop being the source of truth (DD-72).
- **Leave the name, fix only the Swagger text and the documents.** Rejected. The ambiguity stays in the public API, the events and the Policy input, and fixing it after release is a breaking change.

## Risk & Gap Analysis

### Closed in revision 2

| # | Question | Answer |
|---|---|---|
| Q1 | Does any policy on the `dev` Policy server read `afterState.activity.dataProductVersionTag`? | No. Nothing to change on the Policy server. |
| Q2 | Should the column also become `NOT NULL`? | No. The other activity columns are nullable too. |
| Q3 | Do the observer changes go on the BDMD-5558 branch or in a BDMD-5647 branch? | On the current, uncommitted BDMD-5558 branch. |
| Q4 | Has a CLI build with the old name been published? | No. The CLI's DevOps V2 commands are not merged yet. |
| Q5 | Who removes the transition aliases before release? | Not applicable: no aliases are added (DD-69). |

### Edge cases

- **A client sorts by `dataProductVersionTag`.** It gets 400 "invalid sort property". No known client does.
- **Activities in `dev` with a null version.** They keep null. They were already unreachable by natural key, and Policy already received null for them. DD-70 prevents new ones.
- **An event emitted under the old name is delivered after the release.** The Policy adapter and the observer read the version as null. This only affects events that are still in flight at deploy time in `dev`, and the joint release accepts it (DD-69).

### Technical risks

- **Partial deploy.** If one repository ships without the others, it fails: a UI or CLI with the old name stores activities without a version (or is refused by DD-70), and a CLI search ignores the version. Mitigated by DD-69: the five changes ship as one release.
- **The rename misses a JSON string or a test fixture.** Mitigated by the cross-repository grep (release step 3), and by tests that assert the new name in the body, the search, the event and `afterState`.
- **The migration runs against a schema other than `odm_devops`.** `V2` is unqualified, like `V1`, so it runs wherever `V1` ran. The Testcontainers ITs cover `V1` + `V2` with `ddl-auto: validate`.

### Acceptance criteria

1. On `dev`, DevOps starts after `V2`. Existing activities keep their version values under `data_product_version_number`. Flyway and Hibernate validation pass.
2. `GET /activities/{uuid}`, search results and every DevOps V2 activity event carry `dataProductVersionNumber` and not `dataProductVersionTag`.
3. Execute Activity and the four task commands read the version from `dataProductVersionNumber`.
4. Search filters by the `dataProductVersionNumber` query parameter.
5. Execute Activity without a version is refused with 400.
6. The Policy V1 request's `dataProductVersion` still equals the version number. `afterState.activity` carries `dataProductVersionNumber`.
7. The observer reads `dataProductVersionNumber` from DevOps V2 events and resolves the stage version as before.
8. The CLI `start-task` / `complete-task` flow (`PlatformDevOpsCommandIT`) and the UI flow test pass with the new name.
9. `dataProductVersionTag` appears only in Registry (Git tag) code and in `odm-platform`'s copy of the Registry delete event.
10. No SPDD analysis or prompt in DevOps, the CLI, the UI, the agent or the observer uses `dataProductVersionTag`, or "version tag" for the activity's version. Where "version tag" meant the executor ref, it now reads "Git tag".

### Effort

| Repository | Estimate |
|---|---|
| DevOps (migration, rename, required check, tests) | ~3.5 h |
| CLI | ~1.5 h |
| Observer | ~1 h |
| UI and agent | ~0.5 h |
| SPDD analyses and prompts corrected in place | ~3 h |
