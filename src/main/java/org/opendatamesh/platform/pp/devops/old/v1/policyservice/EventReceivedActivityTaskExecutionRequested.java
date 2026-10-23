package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Package-owned view of the inbound ACTIVITY_TASK_EXECUTION_REQUESTED event. Only the fields the adapter reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
class EventReceivedActivityTaskExecutionRequested {
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
        private EventReceivedActivityExecutionRequested.Activity activity;
        private Task task;

        public EventReceivedActivityExecutionRequested.Activity getActivity() {
            return activity;
        }

        public void setActivity(EventReceivedActivityExecutionRequested.Activity activity) {
            this.activity = activity;
        }

        public Task getTask() {
            return task;
        }

        public void setTask(Task task) {
            this.task = task;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Task {
        private String uuid;
        private String name;

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
    }
}
