package org.opendatamesh.platform.pp.devops.utils.parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pipeline parameter placeholder resolution.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
class PipelineParameterPlaceholdersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Feature: Pipeline parameter placeholders
     *
     * Scenario: Results are merged by generation time and objects are sent as JSON
     *   Given task "build" has result {"v":"a","o":{"k":1}} generated first and result {"v":"b"} generated later
     *   When DevOps resolves "${dev.results.build.v}" and "${dev.results.build.o}"
     *   Then the values are "b" and {"k":1}
     */
    @Test
    void whenResultsMergedThenLaterWinsAndObjectsAsJson() throws Exception {
        JsonNode first = objectMapper.readTree("{\"v\":\"a\",\"o\":{\"k\":1}}");
        JsonNode later = objectMapper.readTree("{\"v\":\"b\"}");
        JsonNode merged = PipelineParameterPlaceholders.mergeDocuments(List.of(first, later));

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("value", "${dev.results.build.v}");
        parameters.put("object", "${dev.results.build.o}");

        Map<String, String> resolved = PipelineParameterPlaceholders.resolveAll(
                parameters,
                Map.of("dev", Map.of("build", merged)),
                path -> {
                    throw new AssertionError("unexpected unresolved path " + path);
                }
        );

        assertThat(resolved.get("value")).isEqualTo("b");
        assertThat(resolved.get("object")).isEqualTo("{\"k\":1}");
    }
}
