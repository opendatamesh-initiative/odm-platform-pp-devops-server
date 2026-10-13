package org.opendatamesh.platform.pp.devops.rest.v2.resources.event;

import java.util.Locale;

public enum EventTypeRes {
    ACTIVITY_EXECUTION_REQUESTED,
    ACTIVITY_TASK_EXECUTION_REQUESTED,
    ACTIVITY_SUCCEEDED,
    ACTIVITY_FAILED,
    ACTIVITY_CANCELED,
    ACTIVITY_EXECUTION_APPROVED,
    ACTIVITY_EXECUTION_REJECTED,
    ACTIVITY_TASK_EXECUTION_APPROVED,
    ACTIVITY_TASK_EXECUTION_REJECTED;

    public static EventTypeRes fromString(String value) {
        return EventTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
