package org.opendatamesh.platform.pp.devops.utils.entities;

/**
 * Size limits of the texts DevOps stores, and helpers that make a text from an external system fit them.
 * The REST API refuses a text over its limit; DevOps fits a text it collects itself (an executor log,
 * a failure reason) so that storing the outcome of a run never fails because of it.
 */
public final class StoredTextLimits {

    /** Characters kept in one task log or one task result. */
    public static final int CONTENT_MAX_CHARS = 1_048_576;

    /** Characters of a provider run id (the column is varchar(255)). */
    public static final int PROVIDER_RUN_ID_MAX_CHARS = 255;

    private static final String TRUNCATION_MARKER =
            "[... DevOps kept only the last part of this log: it exceeded " + CONTENT_MAX_CHARS + " characters ...]\n";

    private StoredTextLimits() {
    }

    /**
     * Removes NUL characters, which PostgreSQL text columns refuse.
     */
    public static String withoutNulCharacters(String text) {
        if (text == null || text.indexOf('\u0000') < 0) {
            return text;
        }
        return text.replace("\u0000", "");
    }

    /**
     * Makes a log collected by DevOps fit one stored log: removes NUL characters and,
     * when it is still too long, keeps its end (where a pipeline reports its outcome) after a marker.
     */
    public static String fitLogContent(String content) {
        String clean = withoutNulCharacters(content);
        if (clean == null || clean.length() <= CONTENT_MAX_CHARS) {
            return clean;
        }
        int keep = CONTENT_MAX_CHARS - TRUNCATION_MARKER.length();
        int start = clean.length() - keep;
        if (Character.isLowSurrogate(clean.charAt(start))) {
            start++;
        }
        return TRUNCATION_MARKER + clean.substring(start);
    }
}
