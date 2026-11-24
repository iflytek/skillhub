package com.iflytek.skillhub.githubsync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubSourceTagTest {
    private static final String COMMIT = "d".repeat(40);
    private static final GithubSourceTag INVALID =
            new GithubSourceTag("UNKNOWN", null, null, null, "INVALID_REPORT");

    @Test
    void missingReportStaysUnrecorded() {
        assertThat(GithubSourceTag.fromReport(null)).isNull();
    }

    @Test
    void acceptsTheFourReportedShapes() {
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("EXACT", "v7.0.1", COMMIT, 0, null)))
                .isEqualTo(new GithubSourceTag("EXACT", "v7.0.1", COMMIT, 0, null));
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("AFTER_TAG", "1.3.2-rc.1+b.5", COMMIT, 3, null)))
                .isEqualTo(new GithubSourceTag("AFTER_TAG", "1.3.2-rc.1+b.5", COMMIT, 3, null));
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("NO_TAG", null, null, null, null)))
                .isEqualTo(new GithubSourceTag("NO_TAG", null, null, null, null));
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("UNKNOWN", null, null, null, "SHALLOW_HISTORY")))
                .isEqualTo(new GithubSourceTag("UNKNOWN", null, null, null, "SHALLOW_HISTORY"));
    }

    @Test
    void malformedReportsBecomeInvalidUnknown() {
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("LATEST", null, null, null, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("EXACT", "v7.0.1", COMMIT, 2, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("AFTER_TAG", "v1.3.2", COMMIT, 0, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("AFTER_TAG", "pkg/v1.3.2", COMMIT, 3, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("AFTER_TAG", "release-1", COMMIT, 3, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("EXACT", "v7.0.1", "D".repeat(40), 0, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("NO_TAG", "v1.0.0", null, null, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("UNKNOWN", null, null, null, "bad reason")))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("UNKNOWN", null, null, null, null)))
                .isEqualTo(INVALID);
        assertThat(GithubSourceTag.fromReport(new GithubSourceTag("EXACT",
                "v" + "9".repeat(130) + ".0.0", COMMIT, 0, null))).isEqualTo(INVALID);
    }
}
