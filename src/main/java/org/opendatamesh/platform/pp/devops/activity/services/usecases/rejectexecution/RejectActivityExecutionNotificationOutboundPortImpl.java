package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityFailedRes;

class RejectActivityExecutionNotificationOutboundPortImpl implements RejectActivityExecutionNotificationOutboundPort {

    private final NotificationClient notificationClient;
    private final ActivityMapper activityMapper;

    RejectActivityExecutionNotificationOutboundPortImpl(NotificationClient notificationClient, ActivityMapper activityMapper) {
        this.notificationClient = notificationClient;
        this.activityMapper = activityMapper;
    }

    @Override
    public void emitActivityFailed(Activity activity) {
        EmittedEventActivityFailedRes event = new EmittedEventActivityFailedRes();
        event.setResourceIdentifier(activity.getUuid());
        event.getEventContent().setActivity(activityMapper.toEventRes(activity));
        notificationClient.notifyEvent(event);
    }
}
