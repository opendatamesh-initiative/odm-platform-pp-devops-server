package org.opendatamesh.platform.pp.devops.utils.entities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fitting a text DevOps collects itself (an executor log) into what it can store.
 */
class StoredTextLimitsTest {

    /**
     * Feature: Store the executor log of a run
     *
     * Scenario: A log longer than a stored log keeps its end
     *   Given an executor log of 2,000,000 characters ending with "BUILD SUCCESS"
     *   When DevOps fits it for storage
     *   Then the stored text is exactly the limit long
     *   And it starts with the truncation note and ends with "BUILD SUCCESS"
     */
    @Test
    void whenLogIsTooLongThenItsEndIsKeptWithANote() {
        String log = "x".repeat(2_000_000 - "BUILD SUCCESS".length()) + "BUILD SUCCESS";

        String fitted = StoredTextLimits.fitLogContent(log);

        assertThat(fitted).hasSize(StoredTextLimits.CONTENT_MAX_CHARS);
        assertThat(fitted).startsWith("[... DevOps kept only the last part of this log");
        assertThat(fitted).endsWith("BUILD SUCCESS");
    }

    /**
     * Feature: Store the executor log of a run
     *
     * Scenario: NUL characters, which PostgreSQL text refuses, are removed
     *   Given a short log containing NUL characters
     *   When DevOps fits it for storage
     *   Then the NUL characters are gone and the rest is unchanged
     */
    @Test
    void whenLogHasNulCharactersThenTheyAreRemoved() {
        assertThat(StoredTextLimits.fitLogContent("a\u0000b\u0000c")).isEqualTo("abc");
    }

    /**
     * Feature: Store the executor log of a run
     *
     * Scenario: A log that fits is stored as is
     */
    @Test
    void whenLogFitsThenItIsUnchanged() {
        assertThat(StoredTextLimits.fitLogContent("all good")).isEqualTo("all good");
        assertThat(StoredTextLimits.fitLogContent(null)).isNull();
    }
}
