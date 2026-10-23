package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

/**
 * Judges a Policy V1 response (DD-6): only a failed blocking policy rejects. The aggregate result flag is ignored.
 */
final class PolicyV1Decision {

    private PolicyV1Decision() {
    }

    static boolean hasBlockingFailure(PolicyResValidationResponse response) {
        if (response == null || response.getPolicyResults() == null) {
            return false;
        }
        for (PolicyResPolicyEvaluationResult result : response.getPolicyResults()) {
            if (result != null
                    && Boolean.FALSE.equals(result.getResult())
                    && result.getPolicy() != null
                    && Boolean.TRUE.equals(result.getPolicy().getBlockingFlag())) {
                return true;
            }
        }
        return false;
    }
}
