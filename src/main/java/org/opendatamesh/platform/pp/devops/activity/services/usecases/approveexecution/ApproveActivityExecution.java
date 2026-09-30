package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;

/**
 * Full-control happy path. Brackets run only while the Policy service is inactive.
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
 * This class is {@code ApproveActivityExecution}. On Activity Execution Approved it moves the activity
 * to running and calls {@code AdvanceActivity}.
 */
class ApproveActivityExecution implements UseCase {

    private final ApproveActivityExecutionCommand command;
    private final ApproveActivityExecutionPresenter presenter;
    private final ApproveActivityExecutionPersistenceOutboundPort persistencePort;
    private final ApproveActivityExecutionAdvanceActivityOutboundPort advancePort;
    private final TransactionalOutboundPort transactionalPort;

    ApproveActivityExecution(ApproveActivityExecutionCommand command,
                             ApproveActivityExecutionPresenter presenter,
                             ApproveActivityExecutionPersistenceOutboundPort persistencePort,
                             ApproveActivityExecutionAdvanceActivityOutboundPort advancePort,
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
        Activity activity = approvePendingActivity();
        presenter.presentActivityExecutionApproved(activity);
        advancePort.advanceActivity(activity.getUuid());
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
    }

    private Activity approvePendingActivity() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            requirePending(activity);
            activity.setStatus(ExecutionStatus.RUNNING);
            activity.setStartedAt(now());
            return persistencePort.save(activity);
        }, null);
    }

    private void requirePending(Activity activity) {
        if (activity.getStatus() != ExecutionStatus.PENDING) {
            throw new BadRequestException("Activity " + activity.getUuid() + " can be approved only if PENDING");
        }
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
