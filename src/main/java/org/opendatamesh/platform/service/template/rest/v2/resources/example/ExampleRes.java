package org.opendatamesh.platform.service.template.rest.v2.resources.example;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.service.template.utils.resources.VersionedRes;

@Schema(name = "examples")
public class ExampleRes extends VersionedRes {

    @Schema(description = "The unique identifier of the example")
    private String uuid;

    @Schema(description = "The unique name of the example")
    private String name;

    @Schema(description = "Optional display name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String displayName;

    @Schema(description = "Optional description", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
