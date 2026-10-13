package org.opendatamesh.platform.pp.devops.utils.usecases;

import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;

public interface NotificationEventHandler {
    boolean supportsEventType(EventTypeRes eventType);

    void handleEvent(NotificationDispatchEventRes event);
}
