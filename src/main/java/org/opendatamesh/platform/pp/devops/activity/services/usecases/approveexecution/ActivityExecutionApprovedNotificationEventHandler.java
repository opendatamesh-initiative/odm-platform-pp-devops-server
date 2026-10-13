package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received.ReceivedEventActivityExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityExecutionApprovedNotificationEventHandler implements NotificationEventHandler {

    @Autowired
    private ApproveActivityExecutionFactory approveActivityExecutionFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return eventType.equals(EventTypeRes.ACTIVITY_EXECUTION_APPROVED);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        String uuid = activityUuid(event);
        approveActivityExecutionFactory.buildApproveActivityExecution(
                new ApproveActivityExecutionCommand(uuid),
                activity -> {
                }
        ).execute();
    }

    private String activityUuid(NotificationDispatchEventRes event) {
        ReceivedEventActivityExecutionApprovedRes typedEvent;
        try {
            typedEvent = objectMapper.convertValue(event, ReceivedEventActivityExecutionApprovedRes.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource ReceivedEventActivityExecutionApprovedRes: " + exception.getMessage(),
                    exception
            );
        }
        if (typedEvent == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (typedEvent.getEventContent() == null) {
            throw new BadRequestException("Missing 'eventContent' field in event");
        }
        ReceivedEventActivityExecutionApprovedRes.Activity activity = typedEvent.getEventContent().getActivity();
        if (activity == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        if (activity.getUuid() == null) {
            throw new BadRequestException("Missing 'uuid' field in activity");
        }
        return activity.getUuid();
    }
}
