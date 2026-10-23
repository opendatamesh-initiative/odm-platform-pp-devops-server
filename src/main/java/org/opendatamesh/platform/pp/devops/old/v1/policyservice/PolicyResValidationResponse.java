package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Policy V1 validation response: only the fields the adapter reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyResValidationResponse {
    private Boolean result;
    private List<PolicyResPolicyEvaluationResult> policyResults;

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public List<PolicyResPolicyEvaluationResult> getPolicyResults() {
        return policyResults;
    }

    public void setPolicyResults(List<PolicyResPolicyEvaluationResult> policyResults) {
        this.policyResults = policyResults;
    }
}
