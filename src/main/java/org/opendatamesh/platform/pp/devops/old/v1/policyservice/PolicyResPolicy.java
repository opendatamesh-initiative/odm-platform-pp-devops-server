package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Policy V1 policy: only the fields the adapter reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyResPolicy {
    private Boolean blockingFlag;

    public Boolean getBlockingFlag() {
        return blockingFlag;
    }

    public void setBlockingFlag(Boolean blockingFlag) {
        this.blockingFlag = blockingFlag;
    }
}
