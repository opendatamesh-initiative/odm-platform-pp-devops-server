package org.opendatamesh.platform.pp.devops.rest.v1;

public enum RoutesV1 {

    UP_HEALTH("/api/v1/up/health");
        
    private final String path;
    
    RoutesV1(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}