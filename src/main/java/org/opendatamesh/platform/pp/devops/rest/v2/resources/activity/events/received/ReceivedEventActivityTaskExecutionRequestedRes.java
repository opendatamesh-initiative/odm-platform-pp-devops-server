package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.received;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceivedEventActivityTaskExecutionRequestedRes {

    private Long sequenceId;
    private String resourceType;
    private String resourceIdentifier;
    private String type;
    private String eventTypeVersion;
    private EventContent eventContent;

    public ReceivedEventActivityTaskExecutionRequestedRes() {
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
        private ActivityRes activity;
        private TaskRes task;

        public EventContent() {
        }

        public ActivityRes getActivity() {
            return activity;
        }

        public void setActivity(ActivityRes activity) {
            this.activity = activity;
        }

        public TaskRes getTask() {
            return task;
        }

        public void setTask(TaskRes task) {
            this.task = task;
        }
    }
}
