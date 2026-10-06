package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received.ReceivedEventActivityTaskExecutionRejectedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityTaskExecutionRejectedNotificationEventHandler implements NotificationEventHandler {

    @Autowired
    private RejectTaskExecutionFactory rejectTaskExecutionFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return eventType.equals(EventTypeRes.ACTIVITY_TASK_EXECUTION_REJECTED);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        ReceivedEventActivityTaskExecutionRejectedRes typedEvent = convert(event);
        String activityUuid = activityUuid(typedEvent);
        String taskUuid = taskUuid(typedEvent);
        rejectTaskExecutionFactory.buildRejectTaskExecution(
                new RejectTaskExecutionCommand(activityUuid, taskUuid),
                task -> {
                }
        ).execute();
    }

    private ReceivedEventActivityTaskExecutionRejectedRes convert(NotificationDispatchEventRes event) {
        ReceivedEventActivityTaskExecutionRejectedRes typedEvent;
        try {
            typedEvent = objectMapper.convertValue(event, ReceivedEventActivityTaskExecutionRejectedRes.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource ReceivedEventActivityTaskExecutionRejectedRes: " + exception.getMessage(),
                    exception
            );
        }
        if (typedEvent == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (typedEvent.getEventContent() == null) {
            throw new BadRequestException("Missing 'eventContent' field in event");
        }
        return typedEvent;
    }

    private String activityUuid(ReceivedEventActivityTaskExecutionRejectedRes typedEvent) {
        ReceivedEventActivityTaskExecutionRejectedRes.Activity activity = typedEvent.getEventContent().getActivity();
        if (activity == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        if (activity.getUuid() == null) {
            throw new BadRequestException("Missing 'uuid' field in activity");
        }
        return activity.getUuid();
    }

    private String taskUuid(ReceivedEventActivityTaskExecutionRejectedRes typedEvent) {
        ReceivedEventActivityTaskExecutionRejectedRes.Task task = typedEvent.getEventContent().getTask();
        if (task == null) {
            throw new BadRequestException("Missing 'task' field in event content");
        }
        if (task.getUuid() == null) {
            throw new BadRequestException("Missing 'uuid' field in task");
        }
        return task.getUuid();
    }
}
