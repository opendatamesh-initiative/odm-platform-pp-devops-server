package org.opendatamesh.platform.pp.devops.executor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-memory executor secrets.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
class ExecutorSecretsStoreImplTest {

    /**
     * Feature: Executor client
     *
     * Scenario: Secrets expire and are removed per activity
     *   Given secrets stored for two executors of an activity and for another activity
     *   When the secrets of the first activity are removed
     *   Then nothing is found for the first activity and the other activity keeps its secrets
     */
    @Test
    void whenRemoveAllThenOnlyThatActivityCleared() {
        ExecutorSecretsStoreImpl store = new ExecutorSecretsStoreImpl(new ExecutorSecretsProperties());
        store.store("starter", "activity-1", Map.of("x-odm-token", "one"));
        store.store("cli", "activity-1", Map.of("x-odm-password", "two"));
        store.store("starter", "activity-2", Map.of("x-odm-token", "kept"));

        store.removeAll("activity-1");

        assertThat(store.find("starter", "activity-1")).isEmpty();
        assertThat(store.find("cli", "activity-1")).isEmpty();
        assertThat(store.find("starter", "activity-2")).containsEntry("x-odm-token", "kept");
    }
}
