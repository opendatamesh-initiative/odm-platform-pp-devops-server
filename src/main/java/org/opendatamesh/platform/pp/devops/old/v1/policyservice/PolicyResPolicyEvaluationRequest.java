package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Local copy of the Policy V1 evaluation request. Null fields (for example {@code currentState}) are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyResPolicyEvaluationRequest {
    private ResourceType resourceType;
    private String dataProductId;
    private String dataProductVersion;
    private EventType event;
    private JsonNode currentState;
    private JsonNode afterState;

    public enum EventType {
        DATA_PRODUCT_CREATION,
        DATA_PRODUCT_UPDATE,
        DATA_PRODUCT_VERSION_CREATION,
        ACTIVITY_STAGE_TRANSITION,
        TASK_EXECUTION_RESULT,
        ACTIVITY_EXECUTION_RESULT,
    }

    public enum ResourceType {
        DATA_PRODUCT_DESCRIPTOR,
        ACTIVITY_STAGE_TRANSITION,
        ACTIVITY_EXECUTION_RESULT,
        TASK_EXECUTION_RESULT
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getDataProductId() {
        return dataProductId;
    }

    public void setDataProductId(String dataProductId) {
        this.dataProductId = dataProductId;
    }

    public String getDataProductVersion() {
        return dataProductVersion;
    }

    public void setDataProductVersion(String dataProductVersion) {
        this.dataProductVersion = dataProductVersion;
    }

    public EventType getEvent() {
        return event;
    }

    public void setEvent(EventType event) {
        this.event = event;
    }

    public JsonNode getCurrentState() {
        return currentState;
    }

    public void setCurrentState(JsonNode currentState) {
        this.currentState = currentState;
    }

    public JsonNode getAfterState() {
        return afterState;
    }

    public void setAfterState(JsonNode afterState) {
        this.afterState = afterState;
    }
}
