package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityTaskExecutionRequestedRes;

class RequestTaskExecutionNotificationOutboundPortImpl implements RequestTaskExecutionNotificationOutboundPort {

    private final NotificationClient notificationClient;
    private final ActivityMapper activityMapper;
    private final TaskMapper taskMapper;

    RequestTaskExecutionNotificationOutboundPortImpl(NotificationClient notificationClient,
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
}
