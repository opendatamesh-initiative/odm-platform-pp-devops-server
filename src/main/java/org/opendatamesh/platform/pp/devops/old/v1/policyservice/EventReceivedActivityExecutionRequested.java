package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Package-owned view of the inbound ACTIVITY_EXECUTION_REQUESTED event. Only the fields the adapter reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
class EventReceivedActivityExecutionRequested {
    private String resourceIdentifier;
    private EventContent eventContent;

    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public EventContent getEventContent() {
        return eventContent;
    }

    public void setEventContent(EventContent eventContent) {
        this.eventContent = eventContent;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EventContent {
        private Activity activity;

        public Activity getActivity() {
            return activity;
        }

        public void setActivity(Activity activity) {
            this.activity = activity;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Activity {
        private String uuid;
        private String name;
        private Integer sortOrder;
        private String dataProductVersionUuid;
        private String dataProductFqn;
        private String dataProductVersionTag;

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

        public String getDataProductFqn() {
            return dataProductFqn;
        }

        public void setDataProductFqn(String dataProductFqn) {
            this.dataProductFqn = dataProductFqn;
        }

        public String getDataProductVersionTag() {
            return dataProductVersionTag;
        }

        public void setDataProductVersionTag(String dataProductVersionTag) {
            this.dataProductVersionTag = dataProductVersionTag;
        }
    }
}
