package org.opendatamesh.platform.pp.devops.activity.services.usecases.approvetaskexecutionrequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received.ReceivedEventActivityTaskExecutionRequestedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "odm.product-plane.policy-service.active", havingValue = "false", matchIfMissing = true)
public class ActivityTaskExecutionRequestApproverNotificationEventHandler implements NotificationEventHandler {

    @Autowired
    private ActivityTaskExecutionRequestApproverService approverService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return eventType.equals(EventTypeRes.ACTIVITY_TASK_EXECUTION_REQUESTED);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        ReceivedEventActivityTaskExecutionRequestedRes typedEvent = convert(event);
        ActivityRes activity = activity(typedEvent);
        TaskRes task = task(typedEvent);
        approverService.emitActivityTaskExecutionApprovedEvent(activity, task);
    }

    private ReceivedEventActivityTaskExecutionRequestedRes convert(NotificationDispatchEventRes event) {
        ReceivedEventActivityTaskExecutionRequestedRes typedEvent;
        try {
            typedEvent = objectMapper.convertValue(event, ReceivedEventActivityTaskExecutionRequestedRes.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource ReceivedEventActivityTaskExecutionRequestedRes: " + exception.getMessage(),
                    exception
            );
        }
        if (typedEvent == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (typedEvent.getEventContent() == null) {
            throw new BadRequestException("Missing 'content' field in event");
        }
        return typedEvent;
    }

    private ActivityRes activity(ReceivedEventActivityTaskExecutionRequestedRes typedEvent) {
        ActivityRes activity = typedEvent.getEventContent().getActivity();
        if (activity == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        return activity;
    }

    private TaskRes task(ReceivedEventActivityTaskExecutionRequestedRes typedEvent) {
        TaskRes task = typedEvent.getEventContent().getTask();
        if (task == null) {
            throw new BadRequestException("Missing 'task' field in event content");
        }
        return task;
    }
}
