package org.opendatamesh.platform.pp.devops.activity.services.usecases.approvetaskexecutionrequest;

import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityTaskExecutionApprovedRes;
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
 * This service is the optional task auto-approve. It emits Task Execution Approved.
 */
@Service
public class ActivityTaskExecutionRequestApproverService {

    @Autowired
    private NotificationClient notificationClient;

    public void emitActivityTaskExecutionApprovedEvent(ActivityRes activity, TaskRes task) {
        EmittedEventActivityTaskExecutionApprovedRes.Activity eventActivity = new EmittedEventActivityTaskExecutionApprovedRes.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());

        EmittedEventActivityTaskExecutionApprovedRes.Task eventTask = new EmittedEventActivityTaskExecutionApprovedRes.Task();
        eventTask.setUuid(task.getUuid());
        eventTask.setName(task.getName());

        EmittedEventActivityTaskExecutionApprovedRes eventToEmit = new EmittedEventActivityTaskExecutionApprovedRes();
        eventToEmit.setResourceIdentifier(activity.getUuid());
        eventToEmit.getEventContent().setActivity(eventActivity);
        eventToEmit.getEventContent().setTask(eventTask);
        notificationClient.notifyEvent(eventToEmit);
    }
}
