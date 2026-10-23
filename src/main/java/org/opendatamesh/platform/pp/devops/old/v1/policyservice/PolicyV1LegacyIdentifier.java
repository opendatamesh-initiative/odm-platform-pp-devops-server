package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Computes the legacy fqn-derived data product id that Policy V1 stores.
 * <p>
 * The value equals {@code IdentifierStrategyFactory.getDefault().getId(fqn)} of the DPDS parser 1.x. The parser is
 * deliberately not a dependency because this adapter is temporary. UTF-8 is pinned here (the library uses the
 * platform default charset).
 * <p>
 * Test vectors:
 * <ul>
 *   <li>{@code urn:org.opendatamesh:dataproducts:sample-product} gives {@code a72bbe0c-18c6-30b0-a03a-5392d6a770ea}</li>
 *   <li>{@code urn:org.opendatamesh:dataproducts:other-product} gives the value recorded in {@code PolicyV1LegacyIdentifierTest}</li>
 * </ul>
 */
final class PolicyV1LegacyIdentifier {

    private PolicyV1LegacyIdentifier() {
    }

    static String fromFqn(String fqn) {
        if (fqn == null || fqn.trim().isEmpty()) {
            throw new IllegalArgumentException("Data product fqn is required to compute the Policy V1 data product id");
        }
        return UUID.nameUUIDFromBytes(fqn.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
