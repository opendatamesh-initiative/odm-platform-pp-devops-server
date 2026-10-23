package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds the Policy V1 input document (afterState) from the eventContent of a V2 execution request.
 * <p>
 * The document is the eventContent, copied unchanged, plus what a policy needs and the event does not carry:
 * <ul>
 *   <li>an activity request adds {@code otherActivities}: the other activities of the same data product version, as
 *   summaries without tasks;</li>
 *   <li>a task request adds {@code activity.tasks}: all tasks of the activity, with their results and without their logs.
 *   The requested task is sent as the event carries it.</li>
 * </ul>
 * Dates are ISO-8601 strings in the whole policy input: the dates in the copied event content (epoch milliseconds on the
 * wire) are rewritten here, and the dates added here use the same format.
 * A missing or unknown activity is a defect: the exception propagates and the notification fails.
 */
class PolicyV1InputEnricher {

    private static final Set<String> DATE_FIELDS = Set.of("createdAt", "updatedAt", "startedAt", "finishedAt", "generatedAt");

    private final ActivityService activityService;
    private final ObjectMapper objectMapper;

    PolicyV1InputEnricher(ActivityService activityService, ObjectMapper objectMapper) {
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    ObjectNode forActivityExecution(JsonNode eventContent, String activityUuid, String dataProductVersionUuid) {
        requireText(activityUuid, "uuid");
        requireText(dataProductVersionUuid, "dataProductVersionUuid");
        ObjectNode afterState = eventContent.deepCopy();
        normalizeDates(afterState);
        afterState.set("otherActivities", otherActivities(activityUuid, dataProductVersionUuid));
        return afterState;
    }

    ObjectNode forTaskExecution(JsonNode eventContent, String activityUuid) {
        requireText(activityUuid, "uuid");
        ObjectNode afterState = eventContent.deepCopy();
        normalizeDates(afterState);
        ObjectNode activity = (ObjectNode) afterState.get("activity");
        activity.set("tasks", tasksWithResults(activityUuid));
        return afterState;
    }

    /**
     * The event content carries dates as epoch milliseconds (the outbound RestTemplate keeps its default). The policy
     * input must use ISO-8601 like the other dates it adds, so every numeric value under a known date field is rewritten.
     * Only the policy input changes; the event sent to the other services is not touched.
     */
    private void normalizeDates(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (DATE_FIELDS.contains(name) && child.isNumber()) {
                    object.set(name, dateNode(new Date(child.asLong())));
                } else {
                    normalizeDates(child);
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::normalizeDates);
        }
    }

    private ArrayNode otherActivities(String activityUuid, String dataProductVersionUuid) {
        ActivitySearchOptions filters = new ActivitySearchOptions();
        filters.setDataProductVersionUuid(dataProductVersionUuid);
        List<ActivityRes> siblings = activityService.findAllResourcesFiltered(Pageable.unpaged(), filters)
                .getContent()
                .stream()
                .filter(activity -> !Objects.equals(activityUuid, activity.getUuid()))
                .sorted(bySortOrder(ActivityRes::getSortOrder))
                .toList();

        ArrayNode summaries = objectMapper.createArrayNode();
        siblings.forEach(activity -> summaries.add(activitySummary(activity)));
        return summaries;
    }

    private ObjectNode activitySummary(ActivityRes activity) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("uuid", activity.getUuid());
        summary.put("name", activity.getName());
        summary.put("sortOrder", activity.getSortOrder());
        summary.put("status", activity.getStatus() == null ? null : activity.getStatus().name());
        summary.set("startedAt", dateNode(activity.getStartedAt()));
        summary.set("finishedAt", dateNode(activity.getFinishedAt()));
        return summary;
    }

    private ArrayNode tasksWithResults(String activityUuid) {
        ActivityRes activity = activityService.findOneResource(activityUuid);
        List<TaskRes> tasks = activity.getTasks() == null ? List.of() : activity.getTasks();

        ArrayNode taskNodes = objectMapper.createArrayNode();
        tasks.stream()
                .sorted(bySortOrder(TaskRes::getSortOrder))
                .forEach(task -> taskNodes.add(taskWithResults(task)));
        return taskNodes;
    }

    private ObjectNode taskWithResults(TaskRes task) {
        List<TaskResultRes> results = task.getResults() == null ? List.of() : task.getResults();
        task.setLogs(null);
        task.setResults(null);

        ObjectNode taskNode = objectMapper.valueToTree(task);
        taskNode.remove("logs");
        taskNode.set("results", resultsNode(results));
        return taskNode;
    }

    private ArrayNode resultsNode(List<TaskResultRes> results) {
        ArrayNode resultNodes = objectMapper.createArrayNode();
        for (TaskResultRes result : results) {
            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("uuid", result.getUuid());
            resultNode.put("content", result.getContent());
            resultNode.set("generatedAt", dateNode(result.getGeneratedAt()));
            resultNodes.add(resultNode);
        }
        return resultNodes;
    }

    /** ISO-8601 in UTC with an explicit offset, the format the REST API returns. Independent of the mapper settings. */
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
            .withZone(ZoneOffset.UTC);

    private static JsonNode dateNode(Date date) {
        return date == null ? NullNode.getInstance() : TextNode.valueOf(ISO_DATE.format(date.toInstant()));
    }

    private static <T> Comparator<T> bySortOrder(Function<T, Integer> sortOrder) {
        return Comparator.comparing(sortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("Missing '" + fieldName + "' field in activity");
        }
    }
}
