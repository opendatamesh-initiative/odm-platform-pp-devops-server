package org.opendatamesh.platform.pp.devops.client.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Observer subscriptions at startup.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationClientEventSubscriberTest {

    @Mock
    private NotificationClient notificationClient;
    @Mock
    private NotificationEventHandler handler;

    /**
     * Feature: Observer
     *
     * Scenario: DevOps subscribes at startup to the events it handles
     *   Given the auto-approve handlers and the approved-event handlers are mounted
     *   When the application starts
     *   Then DevOps subscribes to ACTIVITY_EXECUTION_REQUESTED, ACTIVITY_EXECUTION_APPROVED, ACTIVITY_TASK_EXECUTION_REQUESTED, and ACTIVITY_TASK_EXECUTION_APPROVED only
     */
    @Test
    void whenStartedThenSubscribesToHandledEventsOnly() {
        when(handler.supportsEventType(any())).thenAnswer(invocation -> {
            EventTypeRes type = invocation.getArgument(0);
            return type == EventTypeRes.ACTIVITY_EXECUTION_REQUESTED
                    || type == EventTypeRes.ACTIVITY_EXECUTION_APPROVED
                    || type == EventTypeRes.ACTIVITY_TASK_EXECUTION_REQUESTED
                    || type == EventTypeRes.ACTIVITY_TASK_EXECUTION_APPROVED;
        });

        NotificationClientEventSubscriber subscriber = new NotificationClientEventSubscriber();
        ReflectionTestUtils.setField(subscriber, "eventHandlers", List.of(handler));
        ReflectionTestUtils.setField(subscriber, "notificationClient", notificationClient);

        subscriber.afterSingletonsInstantiated();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationClient).subscribeToEvents(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                "ACTIVITY_EXECUTION_REQUESTED",
                "ACTIVITY_TASK_EXECUTION_REQUESTED",
                "ACTIVITY_EXECUTION_APPROVED",
                "ACTIVITY_TASK_EXECUTION_APPROVED"
        );
    }
}
