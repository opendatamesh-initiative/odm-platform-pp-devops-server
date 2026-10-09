package org.opendatamesh.platform.pp.devops.rest.v2;

public enum RoutesV2 {
    ACTIVITIES("/api/v2/pp/devops/activities"),
    ACTIVITIES_EXECUTE("/api/v2/pp/devops/activities/execute"),
    ACTIVITIES_CANCEL("/api/v2/pp/devops/activities/cancel"),
    ACTIVITIES_TASKS_REQUEST_EXECUTION("/api/v2/pp/devops/activities/tasks/request-execution"),
    ACTIVITIES_TASKS_LOGS("/api/v2/pp/devops/activities/tasks/logs"),
    ACTIVITIES_TASKS_RESULTS("/api/v2/pp/devops/activities/tasks/results"),
    ACTIVITIES_TASKS_STATUS("/api/v2/pp/devops/activities/tasks/status"),
    OBSERVER_NOTIFICATIONS("/api/v2/up/observer/notifications");

    private final String path;

    RoutesV2(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
