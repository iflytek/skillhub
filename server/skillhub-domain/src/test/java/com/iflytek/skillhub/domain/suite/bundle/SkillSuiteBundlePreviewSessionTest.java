package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuiteBundlePreviewSessionTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    @Test
    void onlyOwnerCanConfirmReadyUnexpiredPreviewWithExactWarningDigest() {
        SkillSuiteBundlePreviewSession session = preview(NOW.plusSeconds(60));

        assertThatCode(() -> session.requireConfirmableBy("actor", "warning-digest", NOW))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> session.requireConfirmableBy("other", "warning-digest", NOW))
                .isInstanceOf(DomainForbiddenException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.ownerMismatch");
        assertThatThrownBy(() -> session.requireConfirmableBy("actor", "changed", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.warningMismatch");
    }

    @Test
    void expiredOrAlreadyConfirmedPreviewRequiresANewPreview() {
        SkillSuiteBundlePreviewSession expired = preview(NOW);
        assertThatThrownBy(() -> expired.requireConfirmableBy("actor", "warning-digest", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.expired");

        SkillSuiteBundlePreviewSession confirmed = preview(NOW.plusSeconds(60));
        confirmed.markConfirmed(NOW);
        assertThatThrownBy(() -> confirmed.requireConfirmableBy("actor", "warning-digest", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.expired");
    }

    private SkillSuiteBundlePreviewSession preview(Instant expiresAt) {
        return new SkillSuiteBundlePreviewSession(
                "token", "actor", SkillSuiteBundleMode.CREATE, 1L, "suite", null, null,
                "1.0.0", "archive.zip", "a".repeat(64), Map.of("manifest", "value"),
                Map.of("plan", "value"), "warning-digest", expiresAt, NOW.minusSeconds(60));
    }
}
