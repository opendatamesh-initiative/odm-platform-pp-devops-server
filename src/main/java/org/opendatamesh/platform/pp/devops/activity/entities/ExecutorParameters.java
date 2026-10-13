package org.opendatamesh.platform.pp.devops.activity.entities;

/**
 * Value object inside the executor-parameters JSON of a task.
 * Hibernate does not map this type. It has no table.
 */
public class ExecutorParameters {

    private String repositoryKey;
    private DataProductRepo dataProductRepo;
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

    public DataProductRepo getDataProductRepo() {
        return dataProductRepo;
    }

    public void setDataProductRepo(DataProductRepo dataProductRepo) {
        this.dataProductRepo = dataProductRepo;
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
