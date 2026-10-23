package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityTaskExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates ACTIVITY_TASK_EXECUTION_REQUESTED against Policy V1 and emits the V2 approval or rejection.
 * <p>
 * {@code resourceType} is {@code TASK_EXECUTION_RESULT}: Policy V1 {@code PolicyEvaluationRequestResource.ResourceType}
 * declares a value with the same name as the event, and the Policy server does not read {@code resourceType} (policies are
 * selected by {@code event}), so the matching value is the only one that fits the contract.
 */
class NotificationEventHandlerActivityTaskExecutionRequested implements NotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandlerActivityTaskExecutionRequested.class);

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final NotificationClient notificationClient;
    private final PolicyClientV1 policyClient;
    private final PolicyV1InputEnricher inputEnricher;

    NotificationEventHandlerActivityTaskExecutionRequested(NotificationClient notificationClient, PolicyClientV1 policyClient,
                                                           PolicyV1InputEnricher inputEnricher) {
        this.notificationClient = notificationClient;
        this.policyClient = policyClient;
        this.inputEnricher = inputEnricher;
    }

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return EventTypeRes.ACTIVITY_TASK_EXECUTION_REQUESTED.equals(eventType);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        EventReceivedActivityTaskExecutionRequested received = convert(event);
        PolicyResPolicyEvaluationRequest request = buildPolicyRequest(received, event.getEventContent());
        boolean blocked = isBlocked(request, received);
        emitDecision(received.getEventContent(), blocked);
    }

    private EventReceivedActivityTaskExecutionRequested convert(NotificationDispatchEventRes event) {
        EventReceivedActivityTaskExecutionRequested received;
        try {
            received = objectMapper.convertValue(event, EventReceivedActivityTaskExecutionRequested.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource EventReceivedActivityTaskExecutionRequested: " + exception.getMessage(),
                    exception);
        }
        if (received == null) {
            throw new BadRequestException("Event conversion resulted in null");
        }
        if (received.getEventContent() == null) {
            throw new BadRequestException("Missing 'content' field in event");
        }
        if (received.getEventContent().getActivity() == null) {
            throw new BadRequestException("Missing 'activity' field in event content");
        }
        if (received.getEventContent().getTask() == null) {
            throw new BadRequestException("Missing 'task' field in event content");
        }
        return received;
    }

    private PolicyResPolicyEvaluationRequest buildPolicyRequest(EventReceivedActivityTaskExecutionRequested received, JsonNode eventContent) {
        EventReceivedActivityExecutionRequested.Activity activity = received.getEventContent().getActivity();
        PolicyResPolicyEvaluationRequest request = new PolicyResPolicyEvaluationRequest();
        request.setEvent(PolicyResPolicyEvaluationRequest.EventType.TASK_EXECUTION_RESULT);
        request.setResourceType(PolicyResPolicyEvaluationRequest.ResourceType.TASK_EXECUTION_RESULT);
        request.setDataProductId(PolicyV1LegacyIdentifier.fromFqn(activity.getDataProductFqn()));
        request.setDataProductVersion(activity.getDataProductVersionTag());
        request.setAfterState(inputEnricher.forTaskExecution(eventContent, activity.getUuid()));
        return request;
    }

    private boolean isBlocked(PolicyResPolicyEvaluationRequest request, EventReceivedActivityTaskExecutionRequested received) {
        try {
            return PolicyV1Decision.hasBlockingFailure(policyClient.validateInput(request, true));
        } catch (ClientException e) {
            log.warn("Policy client failed for task {}, emitting rejection: code={}",
                    received.getEventContent().getTask().getUuid(), e.getCode());
            return true;
        }
    }

    private void emitDecision(EventReceivedActivityTaskExecutionRequested.EventContent content, boolean rejected) {
        if (rejected) {
            notificationClient.notifyEvent(buildRejected(content));
        } else {
            notificationClient.notifyEvent(buildApproved(content));
        }
    }

    private EmittedEventActivityTaskExecutionApprovedRes buildApproved(EventReceivedActivityTaskExecutionRequested.EventContent content) {
        EventReceivedActivityExecutionRequested.Activity activity = content.getActivity();
        EmittedEventActivityTaskExecutionApprovedRes.Activity eventActivity = new EmittedEventActivityTaskExecutionApprovedRes.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());
        EmittedEventActivityTaskExecutionApprovedRes.Task eventTask = new EmittedEventActivityTaskExecutionApprovedRes.Task();
        eventTask.setUuid(content.getTask().getUuid());
        eventTask.setName(content.getTask().getName());
        EmittedEventActivityTaskExecutionApprovedRes approved = new EmittedEventActivityTaskExecutionApprovedRes();
        approved.setResourceIdentifier(activity.getUuid());
        approved.getEventContent().setActivity(eventActivity);
        approved.getEventContent().setTask(eventTask);
        return approved;
    }

    private EventEmittedActivityTaskExecutionRejected buildRejected(EventReceivedActivityTaskExecutionRequested.EventContent content) {
        EventReceivedActivityExecutionRequested.Activity activity = content.getActivity();
        EventEmittedActivityTaskExecutionRejected.Activity eventActivity = new EventEmittedActivityTaskExecutionRejected.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());
        EventEmittedActivityTaskExecutionRejected.Task eventTask = new EventEmittedActivityTaskExecutionRejected.Task();
        eventTask.setUuid(content.getTask().getUuid());
        eventTask.setName(content.getTask().getName());
        EventEmittedActivityTaskExecutionRejected rejected = new EventEmittedActivityTaskExecutionRejected();
        rejected.setResourceIdentifier(activity.getUuid());
        rejected.getEventContent().setActivity(eventActivity);
        rejected.getEventContent().setTask(eventTask);
        return rejected;
    }
}
