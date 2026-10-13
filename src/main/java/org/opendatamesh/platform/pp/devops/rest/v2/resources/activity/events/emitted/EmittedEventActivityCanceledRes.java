package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted;

import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeVersion;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.ResourceType;

public class EmittedEventActivityCanceledRes {

    private final ResourceType resourceType = ResourceType.ACTIVITY;
    private String resourceIdentifier;
    private final EventTypeRes type = EventTypeRes.ACTIVITY_CANCELED;
    private final EventTypeVersion eventTypeVersion = EventTypeVersion.V2_0_0;
    private EventContent eventContent;

    public EmittedEventActivityCanceledRes() {
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
        private ActivityRes activity;

        public EventContent() {
        }

        public ActivityRes getActivity() {
            return activity;
        }

        public void setActivity(ActivityRes activity) {
            this.activity = activity;
        }
    }
}
