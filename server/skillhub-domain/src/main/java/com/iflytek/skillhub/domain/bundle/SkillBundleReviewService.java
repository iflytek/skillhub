package com.iflytek.skillhub.domain.bundle;

import java.time.Instant;
import java.util.Objects;

/**
 * Drives the bundle review state machine: submit a draft for review, approve, reject.
 *
 * <p>Approval flips both the {@link SkillBundleVersion} status to {@code PUBLISHED}
 * and updates the bundle's latest version pointer. Optimistic locks on the review task
 * guard against concurrent reviewers.
 */
public class SkillBundleReviewService {

    private final SkillBundleRepository bundleRepository;
    private final SkillBundleVersionRepository versionRepository;
    private final SkillBundleReviewTaskRepository reviewTaskRepository;

    public SkillBundleReviewService(SkillBundleRepository bundleRepository,
                                    SkillBundleVersionRepository versionRepository,
                                    SkillBundleReviewTaskRepository reviewTaskRepository) {
        this.bundleRepository = bundleRepository;
        this.versionRepository = versionRepository;
        this.reviewTaskRepository = reviewTaskRepository;
    }

    public SkillBundleReviewTask submitForReview(Long bundleVersionId, String submitter) {
        return submitForReview(bundleVersionId, submitter, Instant.now());
    }

    public SkillBundleReviewTask submitForReview(Long bundleVersionId, String submitter, Instant now) {
        SkillBundleVersion version = versionRepository.findById(bundleVersionId)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.version.notFound"));
        if (version.getStatus() != SkillBundleVersionStatus.DRAFT
                && version.getStatus() != SkillBundleVersionStatus.REJECTED) {
            throw new SkillBundleException("error.skillBundle.version.notSubmittable");
        }
        if (version.getValidationStatus() == BundleValidationStatus.FAILED) {
            throw new SkillBundleException("error.skillBundle.validation.failed");
        }
        if (version.getValidationStatus() == BundleValidationStatus.SCANNING) {
            throw new SkillBundleException("error.skillBundle.validation.scanning");
        }

        SkillBundle bundle = bundleRepository.findById(version.getBundleId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.notFound"));

        version.setStatus(SkillBundleVersionStatus.PENDING_REVIEW);
        versionRepository.save(version);

        SkillBundleReviewTask task = reviewTaskRepository.findByBundleVersionId(bundleVersionId)
                .orElseGet(() -> new SkillBundleReviewTask(bundleVersionId, bundle.getNamespaceId(), submitter));
        task.resubmit(submitter, now);
        return reviewTaskRepository.save(task);
    }

    public SkillBundleReviewTask approve(Long reviewTaskId, String comment, String reviewer, Instant now) {
        SkillBundleReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.reviewTask.notFound"));
        if (!"PENDING".equals(task.getStatus())) {
            throw new SkillBundleException("error.skillBundle.reviewTask.notPending");
        }
        if (Objects.equals(task.getSubmittedBy(), reviewer)) {
            throw new SkillBundleException("error.skillBundle.reviewTask.selfReview");
        }

        SkillBundleVersion version = versionRepository.findById(task.getBundleVersionId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.version.notFound"));
        if (version.getValidationStatus() == BundleValidationStatus.FAILED) {
            throw new SkillBundleException("error.skillBundle.validation.failed");
        }
        if (version.getValidationStatus() == BundleValidationStatus.SCANNING) {
            throw new SkillBundleException("error.skillBundle.validation.scanning");
        }

        int updated = reviewTaskRepository.updateStatusWithVersion(
                task.getId(), "APPROVED", reviewer, comment, task.getVersion());
        if (updated == 0) {
            throw new SkillBundleException("error.skillBundle.reviewTask.concurrentUpdate");
        }

        version.setStatus(SkillBundleVersionStatus.PUBLISHED);
        version.setPublishedAt(now);
        version.setPublishedBy(reviewer);
        versionRepository.save(version);

        SkillBundle bundle = bundleRepository.findById(version.getBundleId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.notFound"));
        bundle.setLatestVersionId(version.getId());
        bundle.setUpdatedAt(now);
        bundle.setUpdatedBy(reviewer);
        bundleRepository.save(bundle);

        return reviewTaskRepository.findById(reviewTaskId).orElseThrow();
    }

    public SkillBundleReviewTask reject(Long reviewTaskId, String comment, String reviewer) {
        SkillBundleReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.reviewTask.notFound"));
        if (!"PENDING".equals(task.getStatus())) {
            throw new SkillBundleException("error.skillBundle.reviewTask.notPending");
        }
        int updated = reviewTaskRepository.updateStatusWithVersion(
                task.getId(), "REJECTED", reviewer, comment, task.getVersion());
        if (updated == 0) {
            throw new SkillBundleException("error.skillBundle.reviewTask.concurrentUpdate");
        }
        SkillBundleVersion version = versionRepository.findById(task.getBundleVersionId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.version.notFound"));
        version.setStatus(SkillBundleVersionStatus.REJECTED);
        version.setRejectReason(comment);
        versionRepository.save(version);
        return reviewTaskRepository.findById(reviewTaskId).orElseThrow();
    }
}
