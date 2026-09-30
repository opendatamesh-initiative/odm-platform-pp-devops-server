package org.opendatamesh.platform.pp.devops.activity.entities;

public class ExecutorParameters {

    private String repositoryKey;
    private RepositoryCoordinates repository;
    private GitRef ref;
    private String pipelineIdentifier;

    public ExecutorParameters() {
    }

    public String getRepositoryKey() {
        return repositoryKey;
    }

    public void setRepositoryKey(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }

    public RepositoryCoordinates getRepository() {
        return repository;
    }

    public void setRepository(RepositoryCoordinates repository) {
        this.repository = repository;
    }

    public GitRef getRef() {
        return ref;
    }

    public void setRef(GitRef ref) {
        this.ref = ref;
    }

    public String getPipelineIdentifier() {
        return pipelineIdentifier;
    }

    public void setPipelineIdentifier(String pipelineIdentifier) {
        this.pipelineIdentifier = pipelineIdentifier;
    }
}
