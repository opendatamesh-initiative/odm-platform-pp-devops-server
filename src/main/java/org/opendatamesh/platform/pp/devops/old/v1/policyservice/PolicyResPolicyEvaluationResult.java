package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Policy V1 single policy evaluation result: only the fields the adapter reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyResPolicyEvaluationResult {
    private Boolean result;
    private PolicyResPolicy policy;

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public PolicyResPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(PolicyResPolicy policy) {
        this.policy = policy;
    }
}
