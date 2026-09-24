package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
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
@RequestMapping(value = "/api/v2/pp/devops/activities", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Activities", description = "Endpoints for managing activities")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @Hidden
    @Operation(
            summary = "Create a new activity",
            description = "Creates a new activity and persists its nested task graph as received. "
                    + "Tasks omitted from the body are not stored. Status defaults to PENDING when omitted."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Activity created successfully",
                    content = @Content(schema = @Schema(implementation = ActivityRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityRes createActivity(
            @Parameter(description = "Activity details including optional nested tasks", required = true)
            @RequestBody ActivityRes activity
    ) {
        return activityService.createResource(activity);
    }

    @Operation(summary = "Get activity by ID", description = "Retrieves a specific activity by its UUID, including nested tasks, logs, and results")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity found",
                    content = @Content(schema = @Schema(implementation = ActivityRes.class))),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public ActivityRes getActivity(
            @Parameter(description = "Activity UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        return activityService.findOneResource(uuid);
    }

    @Operation(summary = "Search activities", description = "Retrieves a paginated list of activities based on search criteria. "
            + "The nested task graph is not loaded. The results can be sorted by any of the following properties: uuid, "
            + "dataProductVersionUuid, dataProductFqn, dataProductVersionTag, name, sortOrder, status, startedAt, "
            + "finishedAt, createdAt, updatedAt. Sort direction can be specified using 'asc' or 'desc' "
            + "(e.g., 'sort=name,desc').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activities found",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or invalid sort property"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<ActivityRes> searchActivities(
            @Parameter(description = "Search options for filtering activities")
            ActivitySearchOptions searchOptions,
            @Parameter(description = "Pagination and sorting parameters. Default sort is by sortOrder in ascending order. "
                    + "Valid sort properties are: uuid, dataProductVersionUuid, dataProductFqn, dataProductVersionTag, "
                    + "name, sortOrder, status, startedAt, finishedAt, createdAt, updatedAt")
            @PageableDefault(page = 0, size = 20, sort = "sortOrder", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return activityService.findAllResourcesFiltered(pageable, searchOptions);
    }

    @Hidden
    @Operation(
            summary = "Update activity",
            description = "Overwrites an existing activity by its UUID. The body is the desired aggregate, including tasks. "
                    + "Tasks omitted from the body are removed."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity updated successfully",
                    content = @Content(schema = @Schema(implementation = ActivityRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public ActivityRes updateActivity(
            @Parameter(description = "Activity UUID", required = true)
            @PathVariable("uuid") String uuid,
            @Parameter(description = "Updated activity details including the desired task graph", required = true)
            @RequestBody ActivityRes activity
    ) {
        return activityService.overwriteResource(uuid, activity);
    }

    @Hidden
    @Operation(summary = "Delete activity", description = "Deletes an activity by its UUID and cascades nested tasks, logs, and results")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Activity deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(
            @Parameter(description = "Activity UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        activityService.delete(uuid);
    }
}
