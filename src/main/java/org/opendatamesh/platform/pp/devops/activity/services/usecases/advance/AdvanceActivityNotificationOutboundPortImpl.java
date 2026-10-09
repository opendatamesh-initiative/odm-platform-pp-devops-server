package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityCanceledRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityFailedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivitySucceededRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityTaskExecutionRequestedRes;

class AdvanceActivityNotificationOutboundPortImpl implements AdvanceActivityNotificationOutboundPort {

    private final NotificationClient notificationClient;
    private final ActivityMapper activityMapper;
    private final TaskMapper taskMapper;

    AdvanceActivityNotificationOutboundPortImpl(NotificationClient notificationClient,
                                                ActivityMapper activityMapper,
                                                TaskMapper taskMapper) {
        this.notificationClient = notificationClient;
        this.activityMapper = activityMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public void emitTaskExecutionRequested(Activity activity, Task task) {
        EmittedEventActivityTaskExecutionRequestedRes event = new EmittedEventActivityTaskExecutionRequestedRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toResWithoutTasks(activity));
        event.getEventContent().setTask(taskMapper.toResWithoutLogsAndResults(task));
        notificationClient.notifyEvent(event);
    }

    @Override
    public void emitActivitySucceeded(Activity activity) {
        EmittedEventActivitySucceededRes event = new EmittedEventActivitySucceededRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toEventRes(activity));
        notificationClient.notifyEvent(event);
    }

    @Override
    public void emitActivityFailed(Activity activity) {
        EmittedEventActivityFailedRes event = new EmittedEventActivityFailedRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toEventRes(activity));
        notificationClient.notifyEvent(event);
    }

    @Override
    public void emitActivityCanceled(Activity activity) {
        EmittedEventActivityCanceledRes event = new EmittedEventActivityCanceledRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toEventRes(activity));
        notificationClient.notifyEvent(event);
    }
}
