package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PolicyV1LegacyIdentifierTest {

    /**
     * Feature: Legacy identifier
     *
     * Scenario: Legacy id matches the DPDS parser output
     *   Given the fqn "urn:org.opendatamesh:dataproducts:sample-product"
     *   When the legacy id is computed
     *   Then it is "a72bbe0c-18c6-30b0-a03a-5392d6a770ea"
     */
    @Test
    public void whenFqnGiven_thenIdMatchesParserVector() {
        // Given
        String fqn = "urn:org.opendatamesh:dataproducts:sample-product";

        // When
        String id = PolicyV1LegacyIdentifier.fromFqn(fqn);

        // Then
        assertThat(id).isEqualTo("a72bbe0c-18c6-30b0-a03a-5392d6a770ea");
        // Second vector: name-based UUID (version 3) of the UTF-8 bytes, the algorithm of IdentifierStrategyFactory default
        assertThat(PolicyV1LegacyIdentifier.fromFqn("urn:org.opendatamesh:dataproducts:other-product"))
                .isEqualTo("cf353fc3-56d0-3ca9-a408-c16a737942b7");
    }

    @Test
    public void whenFqnIsNullOrBlank_thenIllegalArgument() {
        assertThatThrownBy(() -> PolicyV1LegacyIdentifier.fromFqn(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicyV1LegacyIdentifier.fromFqn("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
