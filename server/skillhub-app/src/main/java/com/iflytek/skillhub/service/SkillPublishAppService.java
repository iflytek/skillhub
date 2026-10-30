package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import java.nio.charset.StandardCharsets;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits the skill version and the requested avatar action as one database operation. */
@Service
public class SkillPublishAppService {
    public enum AvatarAction { KEEP, REPLACE, REMOVE }
    private final SkillPublishService publications;
    private final SkillAvatarUploadService uploads;
    private final SkillAvatarAppService avatars;
    private final SkillMetadataParser metadata;
    private ReviewTaskRepository reviews;
    @Autowired
    public void setReviews(ReviewTaskRepository reviews) { this.reviews = reviews; }


    public SkillPublishAppService(SkillPublishService publications, SkillAvatarUploadService uploads,
                                  SkillAvatarAppService avatars, SkillMetadataParser metadata) {
        this.publications = publications;
        this.uploads = uploads;
        this.avatars = avatars;
        this.metadata = metadata;
    }

    @Transactional
    public SkillPublishService.PublishResult publish(String namespace, List<PackageEntry> entries, String actor,
            SkillVisibility visibility, Set<String> roles, boolean confirmWarnings, AvatarAction action,
            String uploadId, String expectedSlug, AuditRequestContext context) {
        return publish(namespace, entries, actor, visibility, roles, confirmWarnings, action,
                uploadId, expectedSlug, null, null, null, false, context);
    }

    @Transactional
    public SkillPublishService.PublishResult publish(String namespace, List<PackageEntry> entries, String actor,
            SkillVisibility visibility, Set<String> roles, boolean confirmWarnings, AvatarAction action,
            String uploadId, String expectedSlug, String displayName, String shortDescription,
            String overview, boolean resetPresentation, AuditRequestContext context) {
        if (action == AvatarAction.REPLACE && (uploadId == null || uploadId.isBlank())
                || action != AvatarAction.REPLACE && uploadId != null && !uploadId.isBlank()) {
            throw new DomainBadRequestException("error.skill.avatar.invalidUpload");
        }
        if (action == AvatarAction.REPLACE || expectedSlug != null && !expectedSlug.isBlank()) {
            PackageEntry skillMd = entries.stream().filter(entry -> "SKILL.md".equals(entry.path())).findFirst()
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.publish.skillMd.notFound"));
            String targetSlug = SlugValidator.slugify(metadata.parse(new String(skillMd.content(), StandardCharsets.UTF_8)).name());
            if (expectedSlug != null && !expectedSlug.isBlank() && !expectedSlug.equals(targetSlug)) {
                throw new DomainBadRequestException("error.skill.avatar.skillMismatch");
            }
            if (action == AvatarAction.REPLACE) {
                uploads.validateReference(namespace, targetSlug, actor, uploadId);
            }
        }
        SkillPublishService.PublishResult result = (shortDescription == null || shortDescription.isBlank())
                && (overview == null || overview.isBlank()) && !resetPresentation
                ? (displayName == null || displayName.isBlank()
                    ? publications.publishFromEntries(namespace, entries, actor, visibility, roles, confirmWarnings)
                    : publications.publishFromEntries(namespace, entries, actor, visibility, roles, confirmWarnings, displayName))
                : publications.publishFromEntries(namespace, entries, actor, visibility, roles, confirmWarnings, displayName,
                        shortDescription, overview, resetPresentation);
        var review = reviews.findBySkillVersionIdAndStatus(
                result.version().getId(), ReviewTaskStatus.PENDING).orElse(null);
        if (action == AvatarAction.REPLACE) {
            uploads.submitForReview(namespace, result.slug(), actor, uploadId, visibility, context, review);
        } else if (action == AvatarAction.REMOVE && review != null) {
            avatars.prepareRemoval(review, namespace, result.slug(), actor);
        } else if (action == AvatarAction.REMOVE) {
            avatars.remove(namespace, result.slug(), actor, false, null, Map.of(), roles, context);
        }
        if (action == AvatarAction.KEEP && review != null) {
            avatars.attachScopeToReview(review.getSkillId(), visibility, review, actor, context);
        }
        return result;
    }
}
