package org.opendatamesh.platform.pp.devops.observer;

import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.exceptions.DevOpsApiException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ObserverService {
    private static final Logger logger = LoggerFactory.getLogger(ObserverService.class);

    @Autowired
    private List<NotificationEventHandler> notificationEventHandlers;

    @Autowired
    private NotificationClient notificationClient;

    @Async
    public void processNotification(NotificationDispatchRes notification) {
        Long notificationId = notification.getSequenceId();
        boolean handled;
        try {
            handleEvent(notification.getEvent());
            handled = true;
        } catch (DevOpsApiException e) {
            logger.warn("Failed to dispatch notification: {}", e.getMessage(), e);
            handled = false;
        } catch (Exception e) {
            logger.error("Failed to dispatch notification: {}", e.getMessage(), e);
            handled = false;
        }
        // The outcome is reported apart from the handling: a failed acknowledgement must not turn an event
        // that took effect into a failure (which invites a replay), and it must not escape this async method.
        try {
            if (handled) {
                notificationClient.processingSuccess(notificationId);
            } else {
                notificationClient.processingFailure(notificationId);
            }
        } catch (Exception e) {
            logger.error("Could not report the {} of notification {} to the Notification service: {}",
                    handled ? "success" : "failure", notificationId, e.getMessage(), e);
        }
    }

    private void handleEvent(NotificationDispatchEventRes event) {
        EventTypeRes eventType;
        try {
            eventType = EventTypeRes.fromString(event.getType());
        } catch (IllegalArgumentException e) {
            logger.info("Unsupported event type: {}. No handler will be used.", event.getType());
            return;
        }

        Optional<NotificationEventHandler> dispatcher = notificationEventHandlers.stream()
                .filter(d -> d.supportsEventType(eventType))
                .findFirst();

        if (!dispatcher.isPresent()) {
            logger.info("No handler found for event type: {}", eventType);
            return;
        }

        dispatcher.get().handleEvent(event);
        logger.info("Event {} handled successfully", eventType);
    }
}
