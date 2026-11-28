package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
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
 * Records the last status of a running instrumented task, then calls Advance Activity.
 * The caller sends the provider run id, or the data product, version number, and activity name.
 */
class RecordTaskStatus implements UseCase {

    private final RecordTaskStatusCommand command;
    private final RecordTaskStatusPresenter presenter;
    private final RecordTaskStatusPersistenceOutboundPort persistencePort;
    private final RecordTaskStatusExecutorOutboundPort executorPort;
    private final RecordTaskStatusAdvanceActivityOutboundPort advancePort;
    private final TransactionalOutboundPort transactionalPort;

    private String providerRunId;
    private String dataProductFqn;
    private String dataProductVersionNumber;
    private String activityName;
    private String taskName;
    /** The task matched by provider run id: it is used as is, never looked up again by name. */
    private Task taskMatchedByRunId;
    private ExecutionStatus status;
    private String activityUuid;

    RecordTaskStatus(RecordTaskStatusCommand command,
                     RecordTaskStatusPresenter presenter,
                     RecordTaskStatusPersistenceOutboundPort persistencePort,
                     RecordTaskStatusExecutorOutboundPort executorPort,
                     RecordTaskStatusAdvanceActivityOutboundPort advancePort,
                     TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
        this.advancePort = advancePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        activityUuid = transactionalPort.doInTransactionWithResults(ignored -> recordTerminalStatus(), null);
        // Also on a repeated report: if the advance failed the first time, the repeat completes it.
        advancePort.advanceActivity(activityUuid);
        presentAdvancedActivity();
    }

    private void validateCommand() {
        providerRunId = command == null ? null : trimToNull(command.providerRunId());
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionNumber = command == null ? null : trimToNull(command.dataProductVersionNumber());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        status = command == null ? null : command.status();
        requireOneAddress();
        if (!StringUtils.hasText(providerRunId) && !StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        if (status != ExecutionStatus.SUCCEEDED && status != ExecutionStatus.FAILED && status != ExecutionStatus.CANCELED) {
            throw new BadRequestException("Task status must be SUCCEEDED, FAILED, or CANCELED");
        }
    }

    /**
     * Stores the final status. The same final status sent again for a task of an open activity is accepted
     * without change, so a client that retries after an error does not get stuck on "not RUNNING".
     */
    private String recordTerminalStatus() {
        Activity activity = loadActivity();
        Task task = resolveTask(activity);
        requireInstrumented(task);
        if (task.getStatus() == status && isOpen(activity.getStatus())) {
            return activity.getUuid();
        }
        requireRunning(task);
        task.setStatus(status);
        task.setFinishedAt(new Timestamp(System.currentTimeMillis()));
        return persistencePort.save(activity).getUuid();
    }

    private static boolean isOpen(ExecutionStatus activityStatus) {
        return activityStatus == ExecutionStatus.PENDING || activityStatus == ExecutionStatus.RUNNING;
    }

    private void presentAdvancedActivity() {
        transactionalPort.doInTransaction(() ->
                presenter.presentTaskStatusRecorded(persistencePort.findActivity(activityUuid))
        );
    }

    private void requireInstrumented(Task task) {
        // A task whose executor is no longer declared is accepted: only its pipeline can still report on it.
        if (executorPort.findExecutionMode(task.getExecutorName()) == ExecutionMode.FULL_CONTROL) {
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
        List<Activity> matches = persistencePort.findOpenActivities(dataProductFqn, dataProductVersionNumber, activityName);
        if (matches.isEmpty()) {
            throw new NotFoundException("No PENDING or RUNNING activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionNumber);
        }
        if (matches.size() > 1) {
            throw new BadRequestException("More than one open activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionNumber);
        }
        return persistencePort.findForUpdate(matches.get(0).getUuid());
    }

    private Task resolveTask(Activity activity) {
        if (taskMatchedByRunId != null) {
            return taskMatchedByRunId;
        }
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
                || StringUtils.hasText(dataProductVersionNumber)
                || StringUtils.hasText(activityName);
        int addressKinds = (hasRunId ? 1 : 0) + (hasNaturalKey ? 1 : 0);
        if (addressKinds == 0) {
            throw new BadRequestException(
                    "Provider run id or data product FQN, version number, and activity name are required"
            );
        }
        if (addressKinds > 1) {
            throw new BadRequestException(
                    "Send only one task address: provider run id, or data product FQN, version number, and activity name"
            );
        }
        if (hasRunId) {
            return;
        }
        if (!StringUtils.hasText(dataProductFqn)) {
            throw new BadRequestException("Data product FQN is required");
        }
        if (!StringUtils.hasText(dataProductVersionNumber)) {
            throw new BadRequestException("Data product version number is required");
        }
        if (!StringUtils.hasText(activityName)) {
            throw new BadRequestException("Activity name is required");
        }
    }

    private Activity activityOfRunningTask() {
        List<Activity> activities = persistencePort.findActivitiesByTaskProviderRunId(providerRunId);
        List<RunningTaskMatch> candidates = runningTasksWithRunId(activities);
        if (candidates.isEmpty()) {
            candidates = repeatedReports(activities);
        }
        noRunningTask(candidates);
        ambiguousRunId(candidates);
        RunningTaskMatch match = candidates.get(0);
        taskName = match.task().getName();
        taskMatchedByRunId = match.task();
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

    /** Tasks of an open activity that already ended with the status sent: a retry of a report that was stored. */
    private List<RunningTaskMatch> repeatedReports(List<Activity> activities) {
        List<RunningTaskMatch> candidates = new ArrayList<>();
        for (Activity activity : activities) {
            if (activity.getTasks() == null) {
                continue;
            }
            for (Task task : activity.getTasks()) {
                boolean sameName = !StringUtils.hasText(taskName) || taskName.equals(task.getName());
                if (task.getStatus() == status && providerRunId.equals(task.getProviderRunId()) && sameName) {
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
