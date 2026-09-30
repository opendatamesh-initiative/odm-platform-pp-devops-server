package org.opendatamesh.platform.pp.devops.rest.v2.resources.event;

import java.util.Locale;

public enum ResourceType {
    ACTIVITY;

    public static ResourceType fromString(String value) {
        return ResourceType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
