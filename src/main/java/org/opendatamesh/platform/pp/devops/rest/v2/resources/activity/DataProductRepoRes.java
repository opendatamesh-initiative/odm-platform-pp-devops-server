package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DataProductRepo")
public class DataProductRepoRes {

    @Schema(description = "Repository provider, as a string such as AZURE, BITBUCKET, GITHUB, or GITLAB")
    private String providerType;

    @Schema(description = "Base URL of the repository provider")
    private String providerBaseUrl;

    @Schema(description = "Provider identifier of the repository")
    private String externalIdentifier;

    @Schema(description = "Repository name")
    private String name;

    @Schema(description = "Owner identifier at the provider")
    private String ownerId;

    @Schema(description = "Owner kind, as a string such as ORGANIZATION or ACCOUNT")
    private String ownerType;

    @Schema(description = "HTTP remote URL of the repository")
    private String remoteUrlHttp;

    @Schema(description = "Default branch of the repository")
    private String defaultBranch;

    public DataProductRepoRes() {
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
