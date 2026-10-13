package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecutionrequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received.ReceivedEventActivityExecutionRequestedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "odm.product-plane.policy-service.active", havingValue = "false", matchIfMissing = true)
public class ActivityExecutionRequestApproverNotificationEventHandler implements NotificationEventHandler {

    @Autowired
    private ActivityExecutionRequestApproverService approverService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return eventType.equals(EventTypeRes.ACTIVITY_EXECUTION_REQUESTED);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        approverService.emitActivityExecutionApprovedEvent(activityFromEvent(event));
    }

    private ActivityRes activityFromEvent(NotificationDispatchEventRes event) {
        ReceivedEventActivityExecutionRequestedRes typedEvent;
        try {
            typedEvent = objectMapper.convertValue(event, ReceivedEventActivityExecutionRequestedRes.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource ReceivedEventActivityExecutionRequestedRes: " + exception.getMessage(),
                    exception
            );
        }
        if (typedEvent == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (typedEvent.getEventContent() == null) {
            throw new BadRequestException("Missing 'content' field in event");
        }
        ActivityRes activity = typedEvent.getEventContent().getActivity();
        if (activity == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        return activity;
    }
}
