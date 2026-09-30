package org.opendatamesh.platform.pp.devops.activity.entities;

public class RepositoryCoordinates {

    private String providerType;
    private String providerBaseUrl;
    private String externalIdentifier;
    private String name;
    private String ownerId;
    private String ownerType;
    private String remoteUrlHttp;
    private String defaultBranch;

    public RepositoryCoordinates() {
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getProviderBaseUrl() {
        return providerBaseUrl;
    }

    public void setProviderBaseUrl(String providerBaseUrl) {
        this.providerBaseUrl = providerBaseUrl;
    }

    public String getExternalIdentifier() {
        return externalIdentifier;
    }

    public void setExternalIdentifier(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getRemoteUrlHttp() {
        return remoteUrlHttp;
    }

    public void setRemoteUrlHttp(String remoteUrlHttp) {
        this.remoteUrlHttp = remoteUrlHttp;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}
