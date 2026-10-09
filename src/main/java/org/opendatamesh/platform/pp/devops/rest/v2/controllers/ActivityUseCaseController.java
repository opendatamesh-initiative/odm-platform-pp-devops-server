package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.devops.activity.services.ActivityUseCasesService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskCommandResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskExecutionRequestCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskLogsCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskResultsCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskStatusCommandRes;
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
                    + "A full-control task runs later, after approval, on the executor it names. "
                    + "An instrumented task is accepted and stays pending until the caller asks for it. "
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

    @Operation(
            summary = "Cancel an activity and return it when it has finished",
            description = "Tasks that have not started are canceled. A running full-control task is asked to stop. "
                    + "A running instrumented task is left running until the caller records a terminal status. "
                    + "The call returns when the activity is succeeded, failed, or canceled."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity succeeded, failed, or canceled",
                    content = @Content(schema = @Schema(implementation = ActivityCancelResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The activity has already terminated"),
            @ApiResponse(responseCode = "409", description = "The activity could not be canceled because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "The activity could not be canceled")
    })
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.OK)
    public ActivityCancelResultRes cancelActivity(
            @Parameter(description = "Activity cancel command", required = true)
            @RequestBody ActivityCancelCommandRes cancelCommand
    ) {
        return useCasesService.cancelActivity(cancelCommand);
    }

    @Operation(
            summary = "Request execution of an instrumented task",
            description = "Addresses the activity by data product, version tag, and activity name. "
                    + "Stores the provider run id of the next pending instrumented task and requests its execution, "
                    + "and only when no other task is running. "
                    + "The call does not start an executor. The task stays pending until approval."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task execution requested",
                    content = @Content(schema = @Schema(implementation = ActivityTaskCommandResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The task cannot be requested"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "409", description = "The task could not be requested because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/tasks/request-execution")
    @ResponseStatus(HttpStatus.OK)
    public ActivityTaskCommandResultRes requestTaskExecution(
            @Parameter(description = "Task execution request", required = true)
            @RequestBody ActivityTaskExecutionRequestCommandRes command
    ) {
        return useCasesService.requestTaskExecution(command);
    }

    @Operation(
            summary = "Record logs of an instrumented task",
            description = "Addresses the activity by data product, version tag, and activity name. "
                    + "Appends logs to an instrumented task. Logs are accepted only while the task is running."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logs stored",
                    content = @Content(schema = @Schema(implementation = ActivityTaskCommandResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The logs cannot be stored"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "409", description = "The logs could not be stored because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/tasks/logs")
    @ResponseStatus(HttpStatus.OK)
    public ActivityTaskCommandResultRes recordTaskLogs(
            @Parameter(description = "Task logs", required = true)
            @RequestBody ActivityTaskLogsCommandRes command
    ) {
        return useCasesService.recordTaskLogs(command);
    }

    @Operation(
            summary = "Record results of a task",
            description = "Appends results to a task of either mode. "
                    + "The caller sends the activity uuid, or the data product, version tag, and activity name. "
                    + "Results are accepted only while the task is running."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Results stored",
                    content = @Content(schema = @Schema(implementation = ActivityTaskCommandResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The results cannot be stored"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "409", description = "The results could not be stored because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/tasks/results")
    @ResponseStatus(HttpStatus.OK)
    public ActivityTaskCommandResultRes recordTaskResults(
            @Parameter(description = "Task results", required = true)
            @RequestBody ActivityTaskResultsCommandRes command
    ) {
        return useCasesService.recordTaskResults(command);
    }

    @Operation(
            summary = "Record the terminal status of an instrumented task",
            description = "Addresses the activity by data product, version tag, and activity name. "
                    + "Records the last status of an instrumented task. "
                    + "Status is the last write and is accepted only while the task is running."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status recorded and the activity advanced",
                    content = @Content(schema = @Schema(implementation = ActivityTaskCommandResultRes.class))),
            @ApiResponse(responseCode = "400", description = "The status cannot be recorded"),
            @ApiResponse(responseCode = "404", description = "Activity not found"),
            @ApiResponse(responseCode = "409", description = "The status could not be recorded because of a concurrent update"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/tasks/status")
    @ResponseStatus(HttpStatus.OK)
    public ActivityTaskCommandResultRes recordTaskStatus(
            @Parameter(description = "Terminal task status", required = true)
            @RequestBody ActivityTaskStatusCommandRes command
    ) {
        return useCasesService.recordTaskStatus(command);
    }
}
