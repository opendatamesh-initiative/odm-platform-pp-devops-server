package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends log rows to one running instrumented task.
 * The caller sends the provider run id, or the data product, version tag, and activity name.
 */
class RecordTaskLogs implements UseCase {

    private final RecordTaskLogsCommand command;
    private final RecordTaskLogsPresenter presenter;
    private final RecordTaskLogsPersistenceOutboundPort persistencePort;
    private final RecordTaskLogsExecutorOutboundPort executorPort;
    private final TransactionalOutboundPort transactionalPort;

    private String providerRunId;
    private String dataProductFqn;
    private String dataProductVersionTag;
    private String activityName;
    private String taskName;
    private List<TaskLog> logs;

    RecordTaskLogs(RecordTaskLogsCommand command,
                   RecordTaskLogsPresenter presenter,
                   RecordTaskLogsPersistenceOutboundPort persistencePort,
                   RecordTaskLogsExecutorOutboundPort executorPort,
                   TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        transactionalPort.doInTransaction(() -> {
            Activity activity = loadActivity();
            Task task = resolveTask(activity);
            requireInstrumented(task);
            requireRunning(task);
            appendTo(task);
            presenter.presentTaskLogsRecorded(persistencePort.save(activity));
        });
    }

    private void validateCommand() {
        providerRunId = command == null ? null : trimToNull(command.providerRunId());
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionTag = command == null ? null : trimToNull(command.dataProductVersionTag());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        requireOneAddress();
        if (!StringUtils.hasText(providerRunId) && !StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        logs = command.logs();
        if (logs == null || logs.isEmpty()) {
            throw new BadRequestException("At least one task log is required");
        }
        for (TaskLog log : logs) {
            normalize(log);
        }
    }

    private void normalize(TaskLog log) {
        if (log == null) {
            throw new BadRequestException("Task log content is required");
        }
        String content = log.getContent() == null ? null : log.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("Task log content is required");
        }
        log.setContent(content);
        log.setUuid(null);
        if (log.getGeneratedAt() == null) {
            log.setGeneratedAt(new Timestamp(System.currentTimeMillis()));
        }
    }

    private void appendTo(Task task) {
        if (task.getLogs() == null) {
            task.setLogs(new ArrayList<>());
        }
        for (TaskLog log : logs) {
            log.setTask(task);
            task.getLogs().add(log);
        }
    }

    private void requireInstrumented(Task task) {
        if (executorPort.findExecutionMode(task.getExecutorName()) != ExecutionMode.INSTRUMENTED) {
            throw new BadRequestException("Task " + taskName + " is not instrumented");
        }
    }

    private void requireRunning(Task task) {
        if (task.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Task " + taskName + " is not RUNNING");
        }
    }

    private Activity loadActivity() {
        if (StringUtils.hasText(providerRunId)) {
            return activityOfRunningTask();
        }
        return openActivityByNaturalKey();
    }

    private Activity openActivityByNaturalKey() {
        List<Activity> matches = persistencePort.findOpenActivities(dataProductFqn, dataProductVersionTag, activityName);
        if (matches.isEmpty()) {
            throw new NotFoundException("No PENDING or RUNNING activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionTag);
        }
        if (matches.size() > 1) {
            throw new BadRequestException("More than one open activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionTag);
        }
        return persistencePort.findForUpdate(matches.get(0).getUuid());
    }

    private Task resolveTask(Activity activity) {
        List<Task> matches = new ArrayList<>();
        if (activity.getTasks() != null) {
            for (Task task : activity.getTasks()) {
                if (taskName.equals(task.getName())) {
                    matches.add(task);
                }
            }
        }
        if (matches.isEmpty()) {
            throw new BadRequestException("Task " + taskName + " not found in activity " + activity.getUuid());
        }
        if (matches.size() > 1) {
            throw new BadRequestException("Task " + taskName + " matches more than one task in activity " + activity.getUuid());
        }
        return matches.get(0);
    }

    private void requireOneAddress() {
        boolean hasRunId = StringUtils.hasText(providerRunId);
        boolean hasNaturalKey = StringUtils.hasText(dataProductFqn)
                || StringUtils.hasText(dataProductVersionTag)
                || StringUtils.hasText(activityName);
        int addressKinds = (hasRunId ? 1 : 0) + (hasNaturalKey ? 1 : 0);
        if (addressKinds == 0) {
            throw new BadRequestException(
                    "Provider run id or data product FQN, version tag, and activity name are required"
            );
        }
        if (addressKinds > 1) {
            throw new BadRequestException(
                    "Send only one task address: provider run id, or data product FQN, version tag, and activity name"
            );
        }
        if (hasRunId) {
            return;
        }
        if (!StringUtils.hasText(dataProductFqn)) {
            throw new BadRequestException("Data product FQN is required");
        }
        if (!StringUtils.hasText(dataProductVersionTag)) {
            throw new BadRequestException("Data product version tag is required");
        }
        if (!StringUtils.hasText(activityName)) {
            throw new BadRequestException("Activity name is required");
        }
    }

    private Activity activityOfRunningTask() {
        List<RunningTaskMatch> candidates = runningTasksWithRunId(persistencePort.findActivitiesWithRunningTask(providerRunId));
        noRunningTask(candidates);
        ambiguousRunId(candidates);
        RunningTaskMatch match = candidates.get(0);
        taskName = match.task().getName();
        return match.activity();
    }

    private List<RunningTaskMatch> runningTasksWithRunId(List<Activity> activities) {
        List<RunningTaskMatch> candidates = new ArrayList<>();
        for (Activity activity : activities) {
            if (activity.getTasks() == null) {
                continue;
            }
            for (Task task : activity.getTasks()) {
                boolean sameName = !StringUtils.hasText(taskName) || taskName.equals(task.getName());
                if (task.getStatus() == ExecutionStatus.RUNNING && providerRunId.equals(task.getProviderRunId()) && sameName) {
                    candidates.add(new RunningTaskMatch(activity, task));
                }
            }
        }
        return candidates;
    }

    private void noRunningTask(List<RunningTaskMatch> candidates) {
        if (candidates.isEmpty()) {
            throw new NotFoundException("No RUNNING task with provider run id " + providerRunId
                    + (StringUtils.hasText(taskName) ? " and task name " + taskName : ""));
        }
    }

    private void ambiguousRunId(List<RunningTaskMatch> candidates) {
        if (candidates.size() > 1) {
            StringBuilder matches = new StringBuilder();
            for (RunningTaskMatch candidate : candidates) {
                if (matches.length() > 0) {
                    matches.append(", ");
                }
                matches.append("task ").append(candidate.task().getName())
                        .append(" of activity ").append(candidate.activity().getUuid());
            }
            throw new BadRequestException("Provider run id " + providerRunId
                    + " matches more than one RUNNING task: " + matches);
        }
    }

    private record RunningTaskMatch(Activity activity, Task task) {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
