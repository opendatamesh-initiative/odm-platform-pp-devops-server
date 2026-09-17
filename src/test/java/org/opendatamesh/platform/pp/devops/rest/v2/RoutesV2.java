package org.opendatamesh.platform.pp.devops.rest.v2;

public enum RoutesV2 {
    ;

    private final String path;

    RoutesV2(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
