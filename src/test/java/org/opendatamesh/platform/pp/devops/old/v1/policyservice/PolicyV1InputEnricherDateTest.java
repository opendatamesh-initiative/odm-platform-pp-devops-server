package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The policy input must write every date as ISO-8601, including the dates copied from the event content, which
 * are epoch milliseconds on the wire. The event content itself is not modified.
 */
class PolicyV1InputEnricherDateTest {

    private static final String ISO_DATE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void activityRequestDatesAreIsoInThePolicyInputAndUnchangedInTheEvent() throws Exception {
        ActivityService activityService = mock(ActivityService.class);
        when(activityService.findAllResourcesFiltered(any(), any())).thenReturn(Page.empty());
        PolicyV1InputEnricher enricher = new PolicyV1InputEnricher(activityService, objectMapper);

        JsonNode eventContent = objectMapper.readTree("""
                {"activity": {"uuid": "a1", "dataProductVersionUuid": "v1", "name": "prod", "sortOrder": 2,
                              "createdAt": 1760000000000, "updatedAt": 1760000000000,
                              "startedAt": null, "finishedAt": null}}
                """);
        String before = eventContent.toString();

        ObjectNode afterState = enricher.forActivityExecution(eventContent, "a1", "v1");

        JsonNode activity = afterState.get("activity");
        assertThat(activity.get("createdAt").isTextual()).isTrue();
        assertThat(activity.get("createdAt").asText()).matches(ISO_DATE);
        assertThat(activity.get("startedAt").isNull()).isTrue();
        assertThat(eventContent.toString()).isEqualTo(before);
    }

    @Test
    void taskRequestDatesAreIsoInThePolicyInput() throws Exception {
        ActivityService activityService = mock(ActivityService.class);
        when(activityService.findOneResource("a1")).thenReturn(new ActivityRes());
        PolicyV1InputEnricher enricher = new PolicyV1InputEnricher(activityService, objectMapper);

        JsonNode eventContent = objectMapper.readTree("""
                {"activity": {"uuid": "a1", "name": "prod", "createdAt": 1760000000000},
                 "task": {"uuid": "t1", "name": "build", "startedAt": 1760000600000}}
                """);

        ObjectNode afterState = enricher.forTaskExecution(eventContent, "a1");

        assertThat(afterState.get("activity").get("createdAt").asText()).matches(ISO_DATE);
        assertThat(afterState.get("task").get("startedAt").asText()).matches(ISO_DATE);
    }
}
