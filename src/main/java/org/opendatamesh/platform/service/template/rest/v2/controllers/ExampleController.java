package org.opendatamesh.platform.service.template.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.service.template.example.services.core.ExampleService;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleRes;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleSearchOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/pp/service-template/examples", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Examples", description = "Sample CRUD endpoints. Replace this aggregate when creating a real service.")
public class ExampleController {

    @Autowired
    private ExampleService exampleService;

    @Operation(summary = "Create a new example", description = "Creates a new sample resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Example created successfully",
                    content = @Content(schema = @Schema(implementation = ExampleRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "409", description = "An example with the same name already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExampleRes createExample(
            @Parameter(description = "Example creation request")
            @RequestBody ExampleRes example
    ) {
        return exampleService.createResource(example);
    }

    @Operation(summary = "Get an example by ID", description = "Retrieves an example by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Example retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ExampleRes.class))),
            @ApiResponse(responseCode = "404", description = "Example not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{uuid}")
    public ExampleRes getExample(
            @Parameter(description = "Example UUID")
            @PathVariable("uuid") String uuid
    ) {
        return exampleService.findOneResource(uuid);
    }

    @Operation(summary = "Search examples", description = "Retrieves a paginated list of examples. "
            + "Sortable properties: uuid, name, displayName, description, createdAt, updatedAt.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Examples found",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or invalid sort property"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<ExampleRes> searchExamples(
            @Parameter(description = "Search options for filtering examples")
            ExampleSearchOptions searchOptions,
            @Parameter(description = "Pagination and sorting parameters. Default sort is createdAt descending.")
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return exampleService.findAllResourcesFiltered(pageable, searchOptions);
    }

    @Operation(summary = "Update example", description = "Updates an existing example by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Example updated successfully",
                    content = @Content(schema = @Schema(implementation = ExampleRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "404", description = "Example not found"),
            @ApiResponse(responseCode = "409", description = "An example with the same name already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public ExampleRes updateExample(
            @Parameter(description = "Example UUID", required = true)
            @PathVariable("uuid") String uuid,
            @Parameter(description = "Updated example details", required = true)
            @RequestBody ExampleRes example
    ) {
        return exampleService.overwriteResource(uuid, example);
    }

    @Operation(summary = "Delete example", description = "Deletes an example by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Example deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Example not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExample(
            @Parameter(description = "Example UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        exampleService.delete(uuid);
    }
}
