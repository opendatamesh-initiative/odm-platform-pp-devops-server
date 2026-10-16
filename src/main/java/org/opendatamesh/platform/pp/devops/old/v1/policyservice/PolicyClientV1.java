package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

/** Client of Policy Service V1. Temporary: removed with the {@code old} package (BDMD-5438). */
public interface PolicyClientV1 {
    PolicyResValidationResponse validateInput(PolicyResPolicyEvaluationRequest evaluationRequest, boolean storeResults);
}
