package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecutionrequest;

import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityExecutionApprovedRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
 * This service is the optional activity auto-approve. It emits Activity Execution Approved for both modes.
 * It does not request a task. Advance Activity emits task execution requested only when the next pending task is full control.
 */
@Service
public class ActivityExecutionRequestApproverService {

    @Autowired
    private NotificationClient notificationClient;

    public void emitActivityExecutionApprovedEvent(ActivityRes activity) {
        EmittedEventActivityExecutionApprovedRes.Activity eventActivity = new EmittedEventActivityExecutionApprovedRes.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());

        EmittedEventActivityExecutionApprovedRes eventToEmit = new EmittedEventActivityExecutionApprovedRes();
        eventToEmit.setResourceIdentifier(activity.getUuid());
        eventToEmit.getEventContent().setActivity(eventActivity);
        notificationClient.notifyEvent(eventToEmit);
    }
}
