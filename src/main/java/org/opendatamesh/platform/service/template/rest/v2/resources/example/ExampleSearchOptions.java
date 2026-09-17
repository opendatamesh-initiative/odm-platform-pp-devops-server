package org.opendatamesh.platform.service.template.rest.v2.resources.example;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

public class ExampleSearchOptions {

    @Parameter(
            description = "Filter examples by name. Case-insensitive substring match (LIKE).",
            schema = @Schema(type = "string")
    )
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
