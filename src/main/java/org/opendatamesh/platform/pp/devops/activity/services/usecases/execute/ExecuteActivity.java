package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutorParameters;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorInfo;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-control happy path. Brackets run only while the Policy service is
 * inactive.
 * 
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
 * 
 * This class is {@code ExecuteActivity}. It creates the activity and emits
 * Activity Execution Requested.
 * An instrumented task is accepted and is not requested by this class.
 * The CLI requests that task later, after the activity is running.
 */
class ExecuteActivity implements UseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExecuteActivityCommand command;
    private final ExecuteActivityPresenter presenter;
    private final ExecuteActivityPersistenceOutboundPort persistencePort;
    private final ExecuteActivityExecutorOutboundPort executorPort;
    private final ExecuteActivitySecretsOutboundPort secretsPort;
    private final ExecuteActivityNotificationOutboundPort notificationPort;
    private final TransactionalOutboundPort transactionalPort;

    private Activity activity;
    private final Set<String> fullControlExecutorNames = new LinkedHashSet<>();

    ExecuteActivity(ExecuteActivityCommand command,
                    ExecuteActivityPresenter presenter,
                    ExecuteActivityPersistenceOutboundPort persistencePort,
                    ExecuteActivityExecutorOutboundPort executorPort,
                    ExecuteActivitySecretsOutboundPort secretsPort,
                    ExecuteActivityNotificationOutboundPort notificationPort,
                    TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
        this.secretsPort = secretsPort;
        this.notificationPort = notificationPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        createAndRequestExecution();
    }

    private void validateCommand() {
        if (command == null || command.activity() == null) {
            throw new BadRequestException("Activity cannot be null");
        }
        activity = command.activity();
        activity.setDataProductVersionUuid(trimToNull(activity.getDataProductVersionUuid()));
        activity.setName(trimToNull(activity.getName()));
        if (!StringUtils.hasText(activity.getDataProductVersionUuid())) {
            throw new BadRequestException("Data product version UUID is required");
        }
        if (!StringUtils.hasText(activity.getName())) {
            throw new BadRequestException("Name is required");
        }
        if (activity.getSortOrder() == null) {
            throw new BadRequestException("Activity sort order is required");
        }
        if (activity.getTasks() == null || activity.getTasks().isEmpty()) {
            throw new BadRequestException("Activity must have at least one task");
        }
        for (Task task : activity.getTasks()) {
            validateTask(task);
        }
    }

    private void validateTask(Task task) {
        if (task == null) {
            throw new BadRequestException("Task entry cannot be null");
        }
        task.setName(trimToNull(task.getName()));
        task.setExecutorName(trimToNull(task.getExecutorName()));
        if (!StringUtils.hasText(task.getName())) {
            throw new BadRequestException("Task name is required");
        }
        if (!StringUtils.hasText(task.getExecutorName())) {
            throw new BadRequestException("Task " + task.getName() + ": executor name is required");
        }
        ExecutorInfo executor = executorPort.findExecutor(task.getExecutorName())
                .orElseThrow(() -> new BadRequestException("Executor " + task.getExecutorName() + " is not declared"));
        if (executor.executionMode() != ExecutionMode.FULL_CONTROL) {
            // not full control, skip validation
            return;
        }
        // full control task: add to fullControlExecutorNames for secrets storage and
        // validate parameters
        fullControlExecutorNames.add(task.getExecutorName());
        if (task.getExecutorParameters() == null || task.getExecutorParameters().isNull() || task.getExecutorParameters().isMissingNode()) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters are required");
        }
        validateExecutorParameters(task);
        validatePipelineParameters(task);
    }

    private void validateExecutorParameters(Task task) {
        ExecutorParameters parameters = readExecutorParameters(task);
        if (parameters.getDataProductRepo() == null || !StringUtils.hasText(parameters.getDataProductRepo().getProviderType())) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters repository provider type is required");
        }
        if (parameters.getRef() == null || !StringUtils.hasText(parameters.getRef().getName())) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters ref name is required");
        }
        if (parameters.getRef().getType() == null) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters ref type is required");
        }
    }

    private void validatePipelineParameters(Task task) {
        Map<String, String> parameters = readPipelineParameters(task);
        if (parameters == null) {
            return;
        }
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (!StringUtils.hasText(parameter.getKey())) {
                throw new BadRequestException("Task " + task.getName() + ": pipeline parameter keys cannot be blank");
            }
            if (parameter.getValue() == null) {
                throw new BadRequestException("Task " + task.getName() + ": pipeline parameter " + parameter.getKey() + " has no value");
            }
        }
    }

    private ExecutorParameters readExecutorParameters(Task task) {
        ExecutorParameters parameters;
        try {
            parameters = OBJECT_MAPPER.treeToValue(task.getExecutorParameters(), ExecutorParameters.class);
        } catch (Exception exception) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters are required");
        }
        if (parameters == null) {
            throw new BadRequestException("Task " + task.getName() + ": executor parameters are required");
        }
        return parameters;
    }

    private Map<String, String> readPipelineParameters(Task task) {
        if (!StringUtils.hasText(task.getPipelineParameters())) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(task.getPipelineParameters(), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception exception) {
            throw new BadRequestException("Task " + task.getName() + ": pipeline parameters are not valid JSON");
        }
    }

    private void createAndRequestExecution() {
        transactionalPort.doInTransaction(() -> {
            refuseIfAnotherExecutionIsOpen();
            prepareForExecution();
            activity = persistencePort.create(activity);
            secretsPort.storeExecutorSecrets(activity, fullControlExecutorNames);
            notificationPort.emitActivityExecutionRequested(activity);
            presenter.presentActivityExecutionRequested(activity);
        });
    }

    private void refuseIfAnotherExecutionIsOpen() {
        List<Activity> open = persistencePort.findActivities(
                activity.getDataProductVersionUuid(),
                activity.getName(),
                EnumSet.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING)
        );
        if (!open.isEmpty()) {
            throw new BadRequestException("Activity " + activity.getName()
                    + " of data product version " + activity.getDataProductVersionUuid()
                    + " is already PENDING or RUNNING");
        }
    }

    private void prepareForExecution() {
        activity.setUuid(null);
        activity.setStatus(ExecutionStatus.PENDING);
        activity.setStartedAt(null);
        activity.setFinishedAt(null);
        List<Task> tasks = activity.getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            task.setUuid(null);
            task.setStatus(ExecutionStatus.PENDING);
            task.setSortOrder(index);
            task.setProviderRunId(null);
            task.setStartedAt(null);
            task.setFinishedAt(null);
            task.setLogs(new ArrayList<>());
            task.setResults(new ArrayList<>());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
