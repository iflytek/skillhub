package com.iflytek.skillhub.githubsync;

import java.util.regex.Pattern;

/**
 * Read-only relation between a fixed acquisition commit and the repository's
 * semver tags. It is captured once with an ingestion and never re-evaluated.
 */
public record GithubSourceTag(String status, String tag, String tagCommitSha,
                              Integer commitsAfterTag, String reason) {
    public static final String EXACT = "EXACT";
    public static final String AFTER_TAG = "AFTER_TAG";
    public static final String NO_TAG = "NO_TAG";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String INVALID_REPORT = "INVALID_REPORT";
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern SEMVER_TAG = Pattern.compile(
            "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");
    private static final Pattern COMMIT = Pattern.compile("^[0-9a-f]{40}$");
    private static final Pattern REASON = Pattern.compile("^[A-Z][A-Z_]{0,47}$");

    /**
     * Normalizes a worker report. A missing report stays absent (not recorded);
     * a malformed report is kept as UNKNOWN so the package itself is still accepted.
     */
    public static GithubSourceTag fromReport(GithubSourceTag reported) {
        if (reported == null) {
            return null;
        }
        String status = reported.status();
        if (EXACT.equals(status) || AFTER_TAG.equals(status)) {
            Integer distance = reported.commitsAfterTag();
            boolean validDistance = distance != null
                    && (EXACT.equals(status) ? distance == 0 : distance > 0);
            if (validDistance && validTag(reported.tag()) && reported.tagCommitSha() != null
                    && COMMIT.matcher(reported.tagCommitSha()).matches() && reported.reason() == null) {
                return new GithubSourceTag(status, reported.tag(), reported.tagCommitSha(), distance, null);
            }
        } else if (NO_TAG.equals(status)) {
            if (reported.tag() == null && reported.tagCommitSha() == null
                    && reported.commitsAfterTag() == null && reported.reason() == null) {
                return new GithubSourceTag(NO_TAG, null, null, null, null);
            }
        } else if (UNKNOWN.equals(status)) {
            if (reported.tag() == null && reported.tagCommitSha() == null && reported.commitsAfterTag() == null
                    && reported.reason() != null && REASON.matcher(reported.reason()).matches()) {
                return new GithubSourceTag(UNKNOWN, null, null, null, reported.reason());
            }
        }
        return invalidReport();
    }

    /** Stored when a worker sends provenance that cannot be trusted. */
    public static GithubSourceTag invalidReport() {
        return new GithubSourceTag(UNKNOWN, null, null, null, INVALID_REPORT);
    }

    private static boolean validTag(String tag) {
        return tag != null && tag.length() <= 128 && SEMVER_TAG.matcher(tag).matches();
    }
}
