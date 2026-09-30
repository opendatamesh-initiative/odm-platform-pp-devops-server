package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ExecutorParameters")
public class ExecutorParametersRes {

    @Schema(description = "Repository key. Null means the main repository.")
    private String repositoryKey;

    @Schema(description = "Coordinates of the repository that holds the pipeline")
    private RepositoryCoordinatesRes repository;

    @Schema(description = "Git ref to check out")
    private GitRefRes ref;

    @Schema(description = "Provider-neutral identifier of the pipeline to start")
    private String pipelineIdentifier;

    public ExecutorParametersRes() {
    }

    public String getRepositoryKey() {
        return repositoryKey;
    }

    public void setRepositoryKey(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }

    public RepositoryCoordinatesRes getRepository() {
        return repository;
    }

    public void setRepository(RepositoryCoordinatesRes repository) {
        this.repository = repository;
    }

    public GitRefRes getRef() {
        return ref;
    }

    public void setRef(GitRefRes ref) {
        this.ref = ref;
    }

    public String getPipelineIdentifier() {
        return pipelineIdentifier;
    }

    public void setPipelineIdentifier(String pipelineIdentifier) {
        this.pipelineIdentifier = pipelineIdentifier;
    }
}
