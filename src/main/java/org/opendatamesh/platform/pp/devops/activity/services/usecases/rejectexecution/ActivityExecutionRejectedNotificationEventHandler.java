package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received.ReceivedEventActivityExecutionRejectedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityExecutionRejectedNotificationEventHandler implements NotificationEventHandler {

    @Autowired
    private RejectActivityExecutionFactory rejectActivityExecutionFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return eventType.equals(EventTypeRes.ACTIVITY_EXECUTION_REJECTED);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        String uuid = activityUuid(event);
        rejectActivityExecutionFactory.buildRejectActivityExecution(
                new RejectActivityExecutionCommand(uuid),
                activity -> {
                }
        ).execute();
    }

    private String activityUuid(NotificationDispatchEventRes event) {
        ReceivedEventActivityExecutionRejectedRes typedEvent;
        try {
            typedEvent = objectMapper.convertValue(event, ReceivedEventActivityExecutionRejectedRes.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource ReceivedEventActivityExecutionRejectedRes: " + exception.getMessage(),
                    exception
            );
        }
        if (typedEvent == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (typedEvent.getEventContent() == null) {
            throw new BadRequestException("Missing 'eventContent' field in event");
        }
        ReceivedEventActivityExecutionRejectedRes.Activity activity = typedEvent.getEventContent().getActivity();
        if (activity == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        if (activity.getUuid() == null) {
            throw new BadRequestException("Missing 'uuid' field in activity");
        }
        return activity.getUuid();
    }
}
