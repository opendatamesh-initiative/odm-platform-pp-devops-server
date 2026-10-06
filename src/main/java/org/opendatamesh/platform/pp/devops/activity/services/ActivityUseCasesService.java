package org.opendatamesh.platform.pp.devops.activity.services;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel.CancelActivityPresenter;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityFactory;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.execute.ExecuteActivityPresenter;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
 * {@code AdvanceActivity} requests the next pending task, or closes the activity.
 * {@code ExecuteTask} polls the executor until the run is terminal, then reads the logs once.
 */
@Service
public class ActivityUseCasesService {

    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ExecuteActivityFactory executeActivityFactory;
    @Autowired
    private CancelActivityFactory cancelActivityFactory;

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
}
