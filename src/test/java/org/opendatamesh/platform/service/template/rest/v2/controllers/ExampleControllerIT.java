package org.opendatamesh.platform.service.template.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.service.template.rest.v2.OdmPlatformServiceApplicationIT;
import org.opendatamesh.platform.service.template.rest.v2.RoutesV2;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class ExampleControllerIT extends OdmPlatformServiceApplicationIT {

    @Test
    public void whenCreateExampleThenReturnCreatedExample() {
        ExampleRes example = newExample("whenCreateExampleThenReturnCreatedExample");

        ResponseEntity<ExampleRes> response = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(example),
                ExampleRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(example.getName());
        assertThat(response.getBody().getDisplayName()).isEqualTo(example.getDisplayName());

        String uuid = response.getBody().getUuid();
        ResponseEntity<ExampleRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.EXAMPLES, "/" + uuid),
                ExampleRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo(example.getName());

        rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + uuid));
    }

    @Test
    public void whenGetExampleByIdThenReturnExample() {
        ResponseEntity<ExampleRes> created = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample("whenGetExampleByIdThenReturnExample")),
                ExampleRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String uuid = created.getBody().getUuid();

        ResponseEntity<ExampleRes> response = rest.getForEntity(
                apiUrl(RoutesV2.EXAMPLES, "/" + uuid),
                ExampleRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isEqualTo(uuid);

        rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + uuid));
    }

    @Test
    public void whenSearchExamplesThenReturnFilteredResults() {
        String namePrefix = "whenSearchExamplesThenReturnFilteredResults";
        ResponseEntity<ExampleRes> first = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample(namePrefix + "-Match")),
                ExampleRes.class
        );
        ResponseEntity<ExampleRes> second = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample(namePrefix + "-other")),
                ExampleRes.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstUuid = first.getBody().getUuid();
        String secondUuid = second.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> filtered = rest.getForEntity(
                    apiUrl(RoutesV2.EXAMPLES, "?name=" + namePrefix + "-Match"),
                    JsonNode.class
            );
            assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(filtered.getBody()).isNotNull();
            JsonNode content = filtered.getBody().get("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.size()).isEqualTo(1);
            assertThat(content.get(0).get("name").asText()).isEqualTo(namePrefix + "-Match");
        } finally {
            rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + firstUuid));
            rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + secondUuid));
        }
    }

    @Test
    public void whenUpdateExampleThenReturnUpdatedExample() {
        String namePrefix = "whenUpdateExampleThenReturnUpdatedExample";
        ResponseEntity<ExampleRes> created = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample(namePrefix)),
                ExampleRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String uuid = created.getBody().getUuid();

        ExampleRes update = newExample(namePrefix + "-updated");
        update.setDescription("updated-description");

        ResponseEntity<ExampleRes> response = rest.exchange(
                apiUrl(RoutesV2.EXAMPLES, "/" + uuid),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                ExampleRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(namePrefix + "-updated");
        assertThat(response.getBody().getDescription()).isEqualTo("updated-description");

        rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + uuid));
    }

    @Test
    public void whenDeleteExampleThenReturnNoContent() {
        ResponseEntity<ExampleRes> created = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample("whenDeleteExampleThenReturnNoContent")),
                ExampleRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String uuid = created.getBody().getUuid();

        ResponseEntity<Void> deleted = rest.exchange(
                apiUrl(RoutesV2.EXAMPLES, "/" + uuid),
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> missing = rest.getForEntity(
                apiUrl(RoutesV2.EXAMPLES, "/" + uuid),
                JsonNode.class
        );
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenCreateExampleWithDuplicateNameThenReturnConflict() {
        String name = "whenCreateExampleWithDuplicateNameThenReturnConflict";
        ResponseEntity<ExampleRes> first = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample(name)),
                ExampleRes.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> conflict = rest.postForEntity(
                apiUrl(RoutesV2.EXAMPLES),
                new HttpEntity<>(newExample(name)),
                JsonNode.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        rest.delete(apiUrl(RoutesV2.EXAMPLES, "/" + first.getBody().getUuid()));
    }

    @Test
    public void whenGetMissingExampleThenReturnNotFound() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.EXAMPLES, "/00000000-0000-0000-0000-000000000000"),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ExampleRes newExample(String name) {
        ExampleRes example = new ExampleRes();
        example.setName(name);
        example.setDisplayName(name + " display");
        example.setDescription("sample");
        return example;
    }
}
