package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.parameters.PipelineParameterPlaceholders;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ExecuteTaskPipelineParametersOutboundPortImpl implements ExecuteTaskPipelineParametersOutboundPort {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteTaskPipelineParametersOutboundPortImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ActivityService activityService;
    private final TransactionalOutboundPort transactionalPort;

    ExecuteTaskPipelineParametersOutboundPortImpl(ActivityService activityService, TransactionalOutboundPort transactionalPort) {
        this.activityService = activityService;
        this.transactionalPort = transactionalPort;
    }

    /**
     * Replaces {@code ${<activityName>.results.<taskName>.<path>}} in pipeline parameter values
     * just before the executor start call. The values stored on the task are not written back.
     * <p>
     * Every activity of the same data product version is loaded, in any status, including the
     * one running now. One activity is kept per name: the latest {@code createdAt}. Inside it,
     * one succeeded task is kept per name, the latest {@code createdAt}. A task that is not
     * {@code SUCCEEDED} is not a candidate, so a newer canceled or failed task does not hide an
     * older succeeded one. An equal timestamp leaves the row already chosen. Stored result rows
     * are not deleted.
     * <p>
     * That task's result rows are parsed as JSON objects. Blank, non-JSON, and non-object rows
     * are skipped. The rest are ordered by {@code generatedAt}, with a missing timestamp last,
     * and merged field by field: a later field replaces an earlier one, and nested objects merge.
     * A scalar becomes its text. An object or an array becomes compact JSON. A missing activity,
     * task, or field is left as the original placeholder, and the warning logs the path and the
     * task uuid. Task results are stored by the CLI inside that task's own pipeline, while it
     * runs, which is out of scope here. Only that pipeline writes that task's results; another
     * pipeline uploading results for a different task is not supported. {@code ExecuteTask} polls
     * until the executor reports a terminal status and then reads the logs, so those rows are
     * already present when the next task resolves placeholders.
     */
    @Override
    public Map<String, String> resolvePipelineParameters(Task task) {
        return transactionalPort.doInTransactionWithResults(ignored -> resolveInsideTransaction(task), null);
    }

    private Map<String, String> resolveInsideTransaction(Task task) {
        ActivitySearchOptions options = new ActivitySearchOptions();
        options.setDataProductVersionUuid(task.getActivity().getDataProductVersionUuid());
        List<Activity> activities = activityService.findAllFiltered(Pageable.unpaged(), options).getContent();
        initializeResults(activities);
        Map<String, Map<String, JsonNode>> context = resultsByActivityAndTask(activities);
        return PipelineParameterPlaceholders.resolveAll(
                readPipelineParameters(task.getPipelineParameters()),
                context,
                path -> logger.warn("Unresolved pipeline parameter placeholder {} in task {}", path, task.getUuid())
        );
    }

    private void initializeResults(List<Activity> activities) {
        for (Activity activity : activities) {
            if (activity.getTasks() == null) {
                continue;
            }
            for (Task task : activity.getTasks()) {
                if (task.getResults() != null) {
                    task.getResults().size();
                }
            }
        }
    }

    private Map<String, Map<String, JsonNode>> resultsByActivityAndTask(List<Activity> activities) {
        Map<String, Activity> latestActivityByName = new LinkedHashMap<>();
        for (Activity activity : activities) {
            if (activity.getName() == null) {
                continue;
            }
            Activity current = latestActivityByName.get(activity.getName());
            if (current == null || isNewer(activity.getCreatedAt(), current.getCreatedAt())) {
                latestActivityByName.put(activity.getName(), activity);
            }
        }
        Map<String, Map<String, JsonNode>> context = new LinkedHashMap<>();
        for (Activity activity : latestActivityByName.values()) {
            Map<String, JsonNode> tasks = latestTaskResults(activity);
            if (!tasks.isEmpty()) {
                context.put(activity.getName(), tasks);
            }
        }
        return context;
    }

    private Map<String, JsonNode> latestTaskResults(Activity activity) {
        Map<String, Task> latestTaskByName = new LinkedHashMap<>();
        if (activity.getTasks() != null) {
            for (Task task : activity.getTasks()) {
                if (task.getName() == null || task.getStatus() != ExecutionStatus.SUCCEEDED) {
                    continue;
                }
                Task current = latestTaskByName.get(task.getName());
                if (current == null || isNewer(task.getCreatedAt(), current.getCreatedAt())) {
                    latestTaskByName.put(task.getName(), task);
                }
            }
        }
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (Task task : latestTaskByName.values()) {
            JsonNode merged = mergeTaskResults(task);
            if (merged != null) {
                results.put(task.getName(), merged);
            }
        }
        return results;
    }

    private JsonNode mergeTaskResults(Task task) {
        if (task.getResults() == null || task.getResults().isEmpty()) {
            return null;
        }
        List<TaskResult> ordered = new ArrayList<>(task.getResults());
        ordered.sort(Comparator.comparing(TaskResult::getGeneratedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<JsonNode> documents = new ArrayList<>();
        for (TaskResult result : ordered) {
            JsonNode document = readObject(result.getContent());
            if (document != null) {
                documents.add(document);
            }
        }
        if (documents.isEmpty()) {
            return null;
        }
        return PipelineParameterPlaceholders.mergeDocuments(documents);
    }

    private JsonNode readObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Map<String, String> readPipelineParameters(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read pipeline parameters");
        }
    }

    private static boolean isNewer(Timestamp candidate, Timestamp incumbent) {
        if (candidate == null) {
            return false;
        }
        if (incumbent == null) {
            return true;
        }
        return candidate.after(incumbent);
    }
}
