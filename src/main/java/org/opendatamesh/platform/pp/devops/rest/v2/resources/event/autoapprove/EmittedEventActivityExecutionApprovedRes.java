package org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove;

import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeVersion;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.ResourceType;

public class EmittedEventActivityExecutionApprovedRes {

    private final ResourceType resourceType = ResourceType.ACTIVITY;
    private String resourceIdentifier;
    private final EventTypeRes type = EventTypeRes.ACTIVITY_EXECUTION_APPROVED;
    private final EventTypeVersion eventTypeVersion = EventTypeVersion.V2_0_0;
    private EventContent eventContent;

    public EmittedEventActivityExecutionApprovedRes() {
        this.eventContent = new EventContent();
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public EventTypeRes getType() {
        return type;
    }

    public String getEventTypeVersion() {
        return eventTypeVersion.toString();
    }

    public EventContent getEventContent() {
        return eventContent;
    }

    public void setEventContent(EventContent eventContent) {
        this.eventContent = eventContent;
    }

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
