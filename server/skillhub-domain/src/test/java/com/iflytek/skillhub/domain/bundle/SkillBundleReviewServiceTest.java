package com.iflytek.skillhub.domain.bundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SkillBundleReviewService}: state machine transitions for the
 * bundle audit lifecycle, including self-review prevention, validation gating,
 * and optimistic-lock contention.
 */
@ExtendWith(MockitoExtension.class)
class SkillBundleReviewServiceTest {

    private SkillBundleRepository bundleRepository;
    private SkillBundleVersionRepository versionRepository;
    private SkillBundleReviewTaskRepository reviewTaskRepository;
    private SkillBundleReviewService service;

    private final Instant now = Instant.parse("2026-06-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        bundleRepository = mock(SkillBundleRepository.class);
        versionRepository = mock(SkillBundleVersionRepository.class);
        reviewTaskRepository = mock(SkillBundleReviewTaskRepository.class);
        service = new SkillBundleReviewService(bundleRepository, versionRepository, reviewTaskRepository);
    }

    @Test
    void submitForReview_blockedWhenValidationStillScanning() {
        SkillBundleVersion version = draftVersion(BundleValidationStatus.SCANNING);
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.submitForReview(120L, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.validation.scanning");
        verify(reviewTaskRepository, never()).save(any());
    }

    @Test
    void submitForReview_blockedWhenValidationFailed() {
        SkillBundleVersion version = draftVersion(BundleValidationStatus.FAILED);
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.submitForReview(120L, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.validation.failed");
    }

    @Test
    void submitForReview_movesVersionToPendingAndCreatesTask() {
        SkillBundleVersion version = draftVersion(BundleValidationStatus.PASSED);
        SkillBundle bundle = bundleStub();
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));
        given(bundleRepository.findById(99L)).willReturn(Optional.of(bundle));
        given(reviewTaskRepository.findByBundleVersionId(120L)).willReturn(Optional.empty());
        given(reviewTaskRepository.save(any(SkillBundleReviewTask.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(versionRepository.save(any(SkillBundleVersion.class))).willAnswer(invocation -> invocation.getArgument(0));

        SkillBundleReviewTask task = service.submitForReview(120L, "alice");

        verify(versionRepository).save(any(SkillBundleVersion.class));
        verify(reviewTaskRepository).save(any(SkillBundleReviewTask.class));
        assertThat(task.getStatus(), "PENDING");
    }

    @Test
    void approve_blockedWhenSubmitterIsReviewer() {
        SkillBundleReviewTask task = pendingTask("alice");
        given(reviewTaskRepository.findById(7L)).willReturn(Optional.of(task));

        assertThatThrownBy(() -> service.approve(7L, "ok", "alice", now))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.reviewTask.selfReview");
        verify(reviewTaskRepository, never()).updateStatusWithVersion(anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void approve_throwsOnConcurrentUpdate() {
        SkillBundleReviewTask task = pendingTask("alice");
        SkillBundleVersion version = draftVersion(BundleValidationStatus.PASSED);
        given(reviewTaskRepository.findById(7L)).willReturn(Optional.of(task));
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));
        given(reviewTaskRepository.updateStatusWithVersion(eq(7L), eq("APPROVED"), anyString(), any(), anyInt()))
                .willReturn(0);

        assertThatThrownBy(() -> service.approve(7L, "ok", "admin", now))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.reviewTask.concurrentUpdate");
    }

    @Test
    void approve_publishesVersionAndUpdatesBundle() {
        SkillBundleReviewTask task = pendingTask("alice");
        SkillBundleVersion version = draftVersion(BundleValidationStatus.PASSED);
        SkillBundle bundle = bundleStub();
        given(reviewTaskRepository.findById(7L)).willReturn(Optional.of(task), Optional.of(task));
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));
        given(bundleRepository.findById(99L)).willReturn(Optional.of(bundle));
        given(reviewTaskRepository.updateStatusWithVersion(eq(7L), eq("APPROVED"), anyString(), any(), anyInt()))
                .willReturn(1);
        given(versionRepository.save(any(SkillBundleVersion.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(bundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.approve(7L, "ok", "admin", now);

        verify(versionRepository, times(1)).save(any(SkillBundleVersion.class));
        verify(bundleRepository, times(1)).save(any(SkillBundle.class));
    }

    @Test
    void reject_setsRejectionReason() {
        SkillBundleReviewTask task = pendingTask("alice");
        SkillBundleVersion version = draftVersion(BundleValidationStatus.PASSED);
        given(reviewTaskRepository.findById(7L)).willReturn(Optional.of(task), Optional.of(task));
        given(versionRepository.findById(120L)).willReturn(Optional.of(version));
        given(reviewTaskRepository.updateStatusWithVersion(eq(7L), eq("REJECTED"), anyString(), any(), anyInt()))
                .willReturn(1);
        given(versionRepository.save(any(SkillBundleVersion.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.reject(7L, "needs work", "admin");

        verify(versionRepository).save(any(SkillBundleVersion.class));
    }

    private SkillBundleVersion draftVersion(BundleValidationStatus validation) {
        SkillBundleVersion v = new SkillBundleVersion(99L, "1.0.0", 1L, "{}", "{}", "key");
        v.setStatus(SkillBundleVersionStatus.DRAFT);
        v.setValidationStatus(validation);
        setField(v, "id", 120L);
        return v;
    }

    private SkillBundleReviewTask pendingTask(String submitter) {
        SkillBundleReviewTask t = new SkillBundleReviewTask(120L, 5L, submitter);
        setField(t, "id", 7L);
        return t;
    }

    private SkillBundle bundleStub() {
        SkillBundle b = new SkillBundle(5L, "ops", "Ops", "summary", SkillBundleType.CUSTOM, "alice", "alice");
        setField(b, "id", 99L);
        return b;
    }

    private void assertThat(String actual, String expected) {
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
