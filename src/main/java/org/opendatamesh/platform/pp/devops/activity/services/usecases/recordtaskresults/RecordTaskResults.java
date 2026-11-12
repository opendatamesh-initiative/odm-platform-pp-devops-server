package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends result rows to a running task of either execution mode.
 * The caller sends the provider run id, the activity uuid, or the data product, version number, and activity name.
 */
class RecordTaskResults implements UseCase {

    private final RecordTaskResultsCommand command;
    private final RecordTaskResultsPresenter presenter;
    private final RecordTaskResultsPersistenceOutboundPort persistencePort;
    private final TransactionalOutboundPort transactionalPort;

    private String providerRunId;
    private String activityUuid;
    private String dataProductFqn;
    private String dataProductVersionNumber;
    private String activityName;
    private String taskName;
    private List<TaskResult> results;

    RecordTaskResults(RecordTaskResultsCommand command,
                      RecordTaskResultsPresenter presenter,
                      RecordTaskResultsPersistenceOutboundPort persistencePort,
                      TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        transactionalPort.doInTransaction(() -> {
            Activity activity = loadActivity();
            Task task = resolveTask(activity);
            requireRunning(task);
            appendTo(task);
            presenter.presentTaskResultsRecorded(persistencePort.save(activity));
        });
    }

    private void validateCommand() {
        providerRunId = command == null ? null : trimToNull(command.providerRunId());
        activityUuid = command == null ? null : trimToNull(command.activityUuid());
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionNumber = command == null ? null : trimToNull(command.dataProductVersionNumber());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        requireOneAddress();
        if (!StringUtils.hasText(providerRunId) && !StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        results = command.results();
        if (results == null || results.isEmpty()) {
            throw new BadRequestException("At least one task result is required");
        }
        for (TaskResult result : results) {
            normalize(result);
        }
    }

    private void normalize(TaskResult result) {
        if (result == null) {
            throw new BadRequestException("Task result content is required");
        }
        String content = result.getContent() == null ? null : result.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("Task result content is required");
        }
        result.setContent(content);
        result.setUuid(null);
        if (result.getGeneratedAt() == null) {
            result.setGeneratedAt(new Timestamp(System.currentTimeMillis()));
        }
    }

    private void appendTo(Task task) {
        if (task.getResults() == null) {
            task.setResults(new ArrayList<>());
        }
        for (TaskResult result : results) {
            result.setTask(task);
            task.getResults().add(result);
        }
    }

    private void requireRunning(Task task) {
        if (task.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Task " + taskName + " is not RUNNING");
        }
    }

    private Activity loadActivity() {
        if (StringUtils.hasText(activityUuid)) {
            return persistencePort.findForUpdate(activityUuid);
        }
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
        boolean hasActivityUuid = StringUtils.hasText(activityUuid);
        int addressKinds = (hasRunId ? 1 : 0) + (hasActivityUuid ? 1 : 0) + (hasNaturalKey ? 1 : 0);
        if (addressKinds == 0) {
            throw new BadRequestException(
                    "Provider run id, activity UUID, or data product FQN, version number, and activity name are required"
            );
        }
        if (addressKinds > 1) {
            throw new BadRequestException(
                    "Send only one task address: provider run id, activity UUID, or data product FQN, version number, and activity name"
            );
        }
        if (hasRunId || hasActivityUuid) {
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
        List<RunningTaskMatch> candidates = runningTasksWithRunId(persistencePort.findActivitiesByTaskProviderRunId(providerRunId));
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
