package org.opendatamesh.platform.service.template.rest.v2;

public enum RoutesV2 {

    EXAMPLES("/api/v2/pp/service-template/examples");

    private final String path;

    RoutesV2(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
