package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceivedEventActivityExecutionApprovedRes {

    private Long sequenceId;
    private String resourceType;
    private String resourceIdentifier;
    private String type;
    private String eventTypeVersion;
    private EventContent eventContent;

    public ReceivedEventActivityExecutionApprovedRes() {
    }

    public Long getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(Long sequenceId) {
        this.sequenceId = sequenceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEventTypeVersion() {
        return eventTypeVersion;
    }

    public void setEventTypeVersion(String eventTypeVersion) {
        this.eventTypeVersion = eventTypeVersion;
    }

    public EventContent getEventContent() {
        return eventContent;
    }

    public void setEventContent(EventContent eventContent) {
        this.eventContent = eventContent;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventContent {
        private Activity activity;

        public EventContent() {
        }

        public Activity getActivity() {
            return activity;
        }

        public void setActivity(Activity activity) {
            this.activity = activity;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Activity {
        private String uuid;
        private String name;
        private Integer sortOrder;
        private String dataProductVersionUuid;

        public Activity() {
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public String getDataProductVersionUuid() {
            return dataProductVersionUuid;
        }

        public void setDataProductVersionUuid(String dataProductVersionUuid) {
            this.dataProductVersionUuid = dataProductVersionUuid;
        }
    }
}
