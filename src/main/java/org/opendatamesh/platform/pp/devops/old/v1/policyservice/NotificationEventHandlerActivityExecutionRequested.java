package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes.NotificationDispatchEventRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates ACTIVITY_EXECUTION_REQUESTED against Policy V1 and emits the V2 approval or rejection.
 * <p>
 * {@code resourceType} is {@code ACTIVITY_EXECUTION_RESULT}: Policy V1 {@code PolicyEvaluationRequestResource.ResourceType}
 * declares a value with the same name as the event, and the Policy server does not read {@code resourceType} (policies are
 * selected by {@code event}), so the matching value is the only one that fits the contract.
 */
class NotificationEventHandlerActivityExecutionRequested implements NotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandlerActivityExecutionRequested.class);

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final NotificationClient notificationClient;
    private final PolicyClientV1 policyClient;
    private final PolicyV1InputEnricher inputEnricher;

    NotificationEventHandlerActivityExecutionRequested(NotificationClient notificationClient, PolicyClientV1 policyClient,
                                                       PolicyV1InputEnricher inputEnricher) {
        this.notificationClient = notificationClient;
        this.policyClient = policyClient;
        this.inputEnricher = inputEnricher;
    }

    @Override
    public boolean supportsEventType(EventTypeRes eventType) {
        return EventTypeRes.ACTIVITY_EXECUTION_REQUESTED.equals(eventType);
    }

    @Override
    public void handleEvent(NotificationDispatchEventRes event) {
        EventReceivedActivityExecutionRequested received = convert(event);
        PolicyResPolicyEvaluationRequest request = buildPolicyRequest(received, event.getEventContent());
        boolean blocked = isBlocked(request, received);
        emitDecision(received.getEventContent().getActivity(), blocked);
    }

    private EventReceivedActivityExecutionRequested convert(NotificationDispatchEventRes event) {
        EventReceivedActivityExecutionRequested received;
        try {
            received = objectMapper.convertValue(event, EventReceivedActivityExecutionRequested.class);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Failed to convert event from JSON to resource EventReceivedActivityExecutionRequested: " + exception.getMessage(),
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
        return received;
    }

    private PolicyResPolicyEvaluationRequest buildPolicyRequest(EventReceivedActivityExecutionRequested received, JsonNode eventContent) {
        EventReceivedActivityExecutionRequested.Activity activity = received.getEventContent().getActivity();
        PolicyResPolicyEvaluationRequest request = new PolicyResPolicyEvaluationRequest();
        request.setEvent(PolicyResPolicyEvaluationRequest.EventType.ACTIVITY_EXECUTION_RESULT);
        request.setResourceType(PolicyResPolicyEvaluationRequest.ResourceType.ACTIVITY_EXECUTION_RESULT);
        request.setDataProductId(PolicyV1LegacyIdentifier.fromFqn(activity.getDataProductFqn()));
        request.setDataProductVersion(activity.getDataProductVersionTag());
        request.setAfterState(inputEnricher.forActivityExecution(
                eventContent, activity.getUuid(), activity.getDataProductVersionUuid()));
        return request;
    }

    private boolean isBlocked(PolicyResPolicyEvaluationRequest request, EventReceivedActivityExecutionRequested received) {
        try {
            return PolicyV1Decision.hasBlockingFailure(policyClient.validateInput(request, true));
        } catch (ClientException e) {
            log.warn("Policy client failed for activity {}, emitting rejection: code={}",
                    received.getEventContent().getActivity().getUuid(), e.getCode());
            return true;
        }
    }

    private void emitDecision(EventReceivedActivityExecutionRequested.Activity activity, boolean rejected) {
        if (rejected) {
            notificationClient.notifyEvent(buildRejected(activity));
        } else {
            notificationClient.notifyEvent(buildApproved(activity));
        }
    }

    private EmittedEventActivityExecutionApprovedRes buildApproved(EventReceivedActivityExecutionRequested.Activity activity) {
        EmittedEventActivityExecutionApprovedRes.Activity eventActivity = new EmittedEventActivityExecutionApprovedRes.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());
        EmittedEventActivityExecutionApprovedRes approved = new EmittedEventActivityExecutionApprovedRes();
        approved.setResourceIdentifier(activity.getUuid());
        approved.getEventContent().setActivity(eventActivity);
        return approved;
    }

    private EventEmittedActivityExecutionRejected buildRejected(EventReceivedActivityExecutionRequested.Activity activity) {
        EventEmittedActivityExecutionRejected.Activity eventActivity = new EventEmittedActivityExecutionRejected.Activity();
        eventActivity.setUuid(activity.getUuid());
        eventActivity.setName(activity.getName());
        eventActivity.setSortOrder(activity.getSortOrder());
        eventActivity.setDataProductVersionUuid(activity.getDataProductVersionUuid());
        EventEmittedActivityExecutionRejected rejected = new EventEmittedActivityExecutionRejected();
        rejected.setResourceIdentifier(activity.getUuid());
        rejected.getEventContent().setActivity(eventActivity);
        return rejected;
    }
}
