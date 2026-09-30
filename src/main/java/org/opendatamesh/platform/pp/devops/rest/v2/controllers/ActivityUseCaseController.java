package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.devops.activity.services.ActivityUseCasesService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/pp/devops/activities", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Activities")
public class ActivityUseCaseController {

    @Autowired
    private ActivityUseCasesService useCasesService;

    @Operation(
            summary = "Execute an activity",
            description = "Creates the activity and its tasks as pending and requests execution. "
                    + "Each task runs later, after approval, on the executor it names. "
                    + "Secret headers use the form x-odm-<executorName>-executor-secret-<secretType>. "
                    + "They are held in memory for that executor and activity, sent to the executor as x-odm-<secretType>, "
                    + "and are never stored, returned, or included in events."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Activity created and execution requested",
                    content = @Content(schema = @Schema(implementation = ActivityExecuteResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The activity cannot be executed"),
            @ApiResponse(responseCode = "409", description = "The activity could not be created because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityExecuteResultRes executeActivity(
            @Parameter(description = "Activity execute command", required = true)
            @RequestBody ActivityExecuteCommandRes executeCommand,
            @Parameter(description = "Optional secret headers named x-odm-<executorName>-executor-secret-<secretType>")
            @RequestHeader HttpHeaders headers
    ) {
        return useCasesService.executeActivity(executeCommand, headers);
    }
}
