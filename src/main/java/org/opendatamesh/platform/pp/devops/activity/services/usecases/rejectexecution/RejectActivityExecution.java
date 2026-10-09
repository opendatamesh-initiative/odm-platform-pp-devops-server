package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;

class RejectActivityExecution implements UseCase {

    private final RejectActivityExecutionCommand command;
    private final RejectActivityExecutionPresenter presenter;
    private final RejectActivityExecutionPersistenceOutboundPort persistencePort;
    private final RejectActivityExecutionNotificationOutboundPort notificationPort;
    private final RejectActivityExecutionSecretsOutboundPort secretsPort;
    private final TransactionalOutboundPort transactionalPort;

    RejectActivityExecution(RejectActivityExecutionCommand command,
                            RejectActivityExecutionPresenter presenter,
                            RejectActivityExecutionPersistenceOutboundPort persistencePort,
                            RejectActivityExecutionNotificationOutboundPort notificationPort,
                            RejectActivityExecutionSecretsOutboundPort secretsPort,
                            TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.notificationPort = notificationPort;
        this.secretsPort = secretsPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        Activity activity = rejectPendingActivity();
        if (activity == null) {
            return;
        }
        presenter.presentActivityExecutionRejected(activity);
        secretsPort.removeExecutorSecrets(command.activityUuid());
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
    }

    private Activity rejectPendingActivity() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            if (activity.getStatus() != ExecutionStatus.PENDING) {
                return null;
            }
            Timestamp finishedAt = now();
            if (activity.getTasks() != null) {
                for (Task task : activity.getTasks()) {
                    task.setStatus(ExecutionStatus.CANCELED);
                    task.setFinishedAt(finishedAt);
                }
            }
            activity.setStatus(ExecutionStatus.FAILED);
            activity.setFinishedAt(finishedAt);
            Activity saved = persistencePort.save(activity);
            notificationPort.emitActivityFailed(saved);
            return saved;
        }, null);
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
