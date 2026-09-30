package org.opendatamesh.platform.pp.devops.rest.v2;

public enum RoutesV2 {
    ACTIVITIES("/api/v2/pp/devops/activities"),
    ACTIVITIES_EXECUTE("/api/v2/pp/devops/activities/execute"),
    OBSERVER_NOTIFICATIONS("/api/v2/up/observer/notifications");

    private final String path;

    RoutesV2(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
