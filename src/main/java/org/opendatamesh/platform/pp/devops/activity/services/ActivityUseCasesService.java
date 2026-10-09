package org.opendatamesh.platform.pp.devops.activity.services;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs.RecordTaskLogsCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs.RecordTaskLogsFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs.RecordTaskLogsPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults.RecordTaskResultsCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults.RecordTaskResultsFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults.RecordTaskResultsPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus.RecordTaskStatusCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus.RecordTaskStatusFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus.RecordTaskStatusPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution.RequestTaskExecutionCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution.RequestTaskExecutionFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution.RequestTaskExecutionPresenter;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts the full-control activity flow. The chain below is the happy path.
 * Brackets run only while the Policy service is inactive. When Policy is active, it emits the approved events.
 * <pre>
 * ExecuteActivity
 *   → Activity Execution Requested
 *   → [auto-approve activity]
 *   → ApproveActivityExecution
 *   → AdvanceActivity
 *   → Task Execution Requested
 *   → [auto-approve task]
 *   → ExecuteTask
 *   → AdvanceActivity
 * </pre>
 * {@code ApproveActivityExecution} and {@code ExecuteTask} call {@code AdvanceActivity} in process.
 * {@code AdvanceActivity} requests the next pending task when that task is full control, or closes the activity.
 * {@code ExecuteTask} polls a full-control executor until the run is terminal, then reads the logs once.
 * For an instrumented task it returns once the task is running.
 * The caller reports an instrumented task through request, logs, results, and status.
 */
@Service
public class ActivityUseCasesService {

    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private ExecuteActivityFactory executeActivityFactory;
    @Autowired
    private CancelActivityFactory cancelActivityFactory;
    @Autowired
    private RequestTaskExecutionFactory requestTaskExecutionFactory;
    @Autowired
    private RecordTaskLogsFactory recordTaskLogsFactory;
    @Autowired
    private RecordTaskResultsFactory recordTaskResultsFactory;
    @Autowired
    private RecordTaskStatusFactory recordTaskStatusFactory;

    public ActivityExecuteResultRes executeActivity(ActivityExecuteCommandRes commandRes, HttpHeaders headers) {
        if (commandRes == null || commandRes.getActivity() == null) {
            throw new BadRequestException("Activity cannot be null");
        }
        ExecuteActivityCommand command = new ExecuteActivityCommand(activityMapper.toEntity(commandRes.getActivity()));
        ActivityExecuteResultHolder holder = new ActivityExecuteResultHolder(activityMapper);
        executeActivityFactory.buildExecuteActivity(command, holder, headers).execute();
        return new ActivityExecuteResultRes(holder.getResult());
    }

    public ActivityCancelResultRes cancelActivity(ActivityCancelCommandRes commandRes) {
        if (commandRes == null || commandRes.getActivity() == null) {
            throw new BadRequestException("Activity cannot be null");
        }
        if (!StringUtils.hasText(commandRes.getActivity().getUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
        CancelActivityCommand command = new CancelActivityCommand(commandRes.getActivity().getUuid());
        ActivityCancelResultHolder holder = new ActivityCancelResultHolder(activityMapper);
        cancelActivityFactory.buildCancelActivity(command, holder).execute();
        return new ActivityCancelResultRes(holder.getResult());
    }

    public ActivityTaskCommandResultRes requestTaskExecution(ActivityTaskExecutionRequestCommandRes commandRes) {
        RequestTaskExecutionCommand command = commandRes == null
                ? new RequestTaskExecutionCommand(null, null, null, null, null)
                : new RequestTaskExecutionCommand(
                        commandRes.getDataProductFqn(),
                        commandRes.getDataProductVersionTag(),
                        commandRes.getActivityName(),
                        commandRes.getTaskName(),
                        commandRes.getProviderRunId()
                );
        ActivityTaskCommandResultHolder holder = new ActivityTaskCommandResultHolder(activityMapper);
        requestTaskExecutionFactory.buildRequestTaskExecution(command, holder).execute();
        return new ActivityTaskCommandResultRes(holder.getResult());
    }

    public ActivityTaskCommandResultRes recordTaskLogs(ActivityTaskLogsCommandRes commandRes) {
        RecordTaskLogsCommand command = commandRes == null
                ? new RecordTaskLogsCommand(null, null, null, null, null)
                : new RecordTaskLogsCommand(
                        commandRes.getDataProductFqn(),
                        commandRes.getDataProductVersionTag(),
                        commandRes.getActivityName(),
                        commandRes.getTaskName(),
                        toLogs(commandRes.getLogs())
                );
        ActivityTaskCommandResultHolder holder = new ActivityTaskCommandResultHolder(activityMapper);
        recordTaskLogsFactory.buildRecordTaskLogs(command, holder).execute();
        return new ActivityTaskCommandResultRes(holder.getResult());
    }

    public ActivityTaskCommandResultRes recordTaskResults(ActivityTaskResultsCommandRes commandRes) {
        RecordTaskResultsCommand command = commandRes == null
                ? new RecordTaskResultsCommand(null, null, null, null, null, null)
                : new RecordTaskResultsCommand(
                        commandRes.getActivityUuid(),
                        commandRes.getDataProductFqn(),
                        commandRes.getDataProductVersionTag(),
                        commandRes.getActivityName(),
                        commandRes.getTaskName(),
                        toResults(commandRes.getResults())
                );
        ActivityTaskCommandResultHolder holder = new ActivityTaskCommandResultHolder(activityMapper);
        recordTaskResultsFactory.buildRecordTaskResults(command, holder).execute();
        return new ActivityTaskCommandResultRes(holder.getResult());
    }

    public ActivityTaskCommandResultRes recordTaskStatus(ActivityTaskStatusCommandRes commandRes) {
        RecordTaskStatusCommand command = commandRes == null
                ? new RecordTaskStatusCommand(null, null, null, null, null)
                : new RecordTaskStatusCommand(
                        commandRes.getDataProductFqn(),
                        commandRes.getDataProductVersionTag(),
                        commandRes.getActivityName(),
                        commandRes.getTaskName(),
                        commandRes.getStatus()
                );
        ActivityTaskCommandResultHolder holder = new ActivityTaskCommandResultHolder(activityMapper);
        recordTaskStatusFactory.buildRecordTaskStatus(command, holder).execute();
        return new ActivityTaskCommandResultRes(holder.getResult());
    }

    private List<TaskLog> toLogs(List<TaskLogRes> logs) {
        if (logs == null) {
            return null;
        }
        List<TaskLog> entities = new ArrayList<>();
        for (TaskLogRes log : logs) {
            entities.add(toLog(log));
        }
        return entities;
    }

    private TaskLog toLog(TaskLogRes log) {
        if (log == null) {
            return null;
        }
        TaskLog entity = taskMapper.toEntity(log);
        entity.setUuid(null);
        return entity;
    }

    private List<TaskResult> toResults(List<TaskResultRes> results) {
        if (results == null) {
            return null;
        }
        List<TaskResult> entities = new ArrayList<>();
        for (TaskResultRes result : results) {
            entities.add(toResult(result));
        }
        return entities;
    }

    private TaskResult toResult(TaskResultRes result) {
        if (result == null) {
            return null;
        }
        TaskResult entity = taskMapper.toEntity(result);
        entity.setUuid(null);
        return entity;
    }

    private static final class ActivityExecuteResultHolder implements ExecuteActivityPresenter {
        private final ActivityMapper activityMapper;
        private ActivityRes result;

        private ActivityExecuteResultHolder(ActivityMapper activityMapper) {
            this.activityMapper = activityMapper;
        }

        @Override
        public void presentActivityExecutionRequested(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        private ActivityRes getResult() {
            return result;
        }
    }

    private static final class ActivityCancelResultHolder implements CancelActivityPresenter {
        private final ActivityMapper activityMapper;
        private ActivityRes result;

        private ActivityCancelResultHolder(ActivityMapper activityMapper) {
            this.activityMapper = activityMapper;
        }

        @Override
        public void presentCancelCompleted(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        private ActivityRes getResult() {
            return result;
        }
    }

    private static final class ActivityTaskCommandResultHolder implements RequestTaskExecutionPresenter,
            RecordTaskLogsPresenter, RecordTaskResultsPresenter, RecordTaskStatusPresenter {
        private final ActivityMapper activityMapper;
        private ActivityRes result;

        private ActivityTaskCommandResultHolder(ActivityMapper activityMapper) {
            this.activityMapper = activityMapper;
        }

        @Override
        public void presentTaskExecutionRequested(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        @Override
        public void presentTaskLogsRecorded(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        @Override
        public void presentTaskResultsRecorded(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        @Override
        public void presentTaskStatusRecorded(Activity activity) {
            this.result = activityMapper.toRes(activity);
        }

        private ActivityRes getResult() {
            return result;
        }
    }
}
