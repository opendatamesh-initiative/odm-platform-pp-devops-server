package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityExecutionRequestedRes;

class ExecuteActivityNotificationOutboundPortImpl implements ExecuteActivityNotificationOutboundPort {

    private final NotificationClient notificationClient;
    private final ActivityMapper activityMapper;

    ExecuteActivityNotificationOutboundPortImpl(NotificationClient notificationClient, ActivityMapper activityMapper) {
        this.notificationClient = notificationClient;
        this.activityMapper = activityMapper;
    }

    @Override
    public void emitActivityExecutionRequested(Activity activity) {
        EmittedEventActivityExecutionRequestedRes event = new EmittedEventActivityExecutionRequestedRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toEventRes(activity));
        notificationClient.notifyEvent(event);
    }
}
