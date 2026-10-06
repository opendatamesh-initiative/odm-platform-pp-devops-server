package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;

class RejectTaskExecution implements UseCase {

    private final RejectTaskExecutionCommand command;
    private final RejectTaskExecutionPresenter presenter;
    private final RejectTaskExecutionPersistenceOutboundPort persistencePort;
    private final RejectTaskExecutionAdvanceActivityOutboundPort advancePort;
    private final TransactionalOutboundPort transactionalPort;

    RejectTaskExecution(RejectTaskExecutionCommand command,
                        RejectTaskExecutionPresenter presenter,
                        RejectTaskExecutionPersistenceOutboundPort persistencePort,
                        RejectTaskExecutionAdvanceActivityOutboundPort advancePort,
                        TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.advancePort = advancePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        Task rejected = rejectPendingTask();
        if (rejected == null) {
            return;
        }
        presenter.presentTaskExecutionRejected(rejected);
        advancePort.advanceActivity(command.activityUuid());
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
        if (!StringUtils.hasText(command.taskUuid())) {
            throw new BadRequestException("Task UUID is required");
        }
    }

    private Task rejectPendingTask() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            Task task = findTask(activity, command.taskUuid());
            if (activity.getStatus() != ExecutionStatus.RUNNING || task.getStatus() != ExecutionStatus.PENDING) {
                return null;
            }
            task.setStatus(ExecutionStatus.FAILED);
            task.setFinishedAt(now());
            persistencePort.save(activity);
            task.getActivity().getUuid();
            return task;
        }, null);
    }

    private Task findTask(Activity activity, String taskUuid) {
        if (activity.getTasks() != null) {
            for (Task task : activity.getTasks()) {
                if (taskUuid.equals(task.getUuid())) {
                    return task;
                }
            }
        }
        throw new NotFoundException("Task " + taskUuid + " not found in activity " + activity.getUuid());
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
