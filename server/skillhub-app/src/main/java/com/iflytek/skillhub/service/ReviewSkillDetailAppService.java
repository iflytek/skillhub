package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewSkillDetailAppService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewService reviewService;
    private final RbacService rbacService;
    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;

    public ReviewSkillDetailAppService(ReviewTaskRepository reviewTaskRepository,
                                       NamespaceRepository namespaceRepository,
                                       ReviewService reviewService,
                                       RbacService rbacService,
                                       SkillQueryService skillQueryService,
                                       SkillDownloadService skillDownloadService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.namespaceRepository = namespaceRepository;
        this.reviewService = reviewService;
        this.rbacService = rbacService;
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
    }

    public ReviewSkillDetailResponse getReviewSkillDetail(Long reviewId,
                                                          String userId,
                                                          Map<Long, NamespaceRole> userNsRoles) {
        ReviewAccessContext context = loadAuthorizedContext(reviewId, userId, userNsRoles);
        SkillQueryService.ReviewSkillSnapshotDTO snapshot =
                skillQueryService.getReviewSkillSnapshot(context.task().getSkillVersionId());

        SkillDetailResponse skill = new SkillDetailResponse(
                snapshot.skill().getId(),
                snapshot.skill().getSlug(),
                snapshot.skill().getDisplayName(),
                snapshot.ownerDisplayName(),
                snapshot.skill().getSummary(),
                snapshot.skill().getVisibility().name(),
                snapshot.skill().getStatus().name(),
                snapshot.skill().getDownloadCount(),
                snapshot.skill().getStarCount(),
                snapshot.skill().getRatingAvg(),
                snapshot.skill().getRatingCount(),
                snapshot.skill().isHidden(),
                context.namespace().getSlug(),
                false,
                false,
                false,
                false,
                toLifecycleVersion(snapshot.activeVersion()),
                snapshot.publishedVersion() != null ? toLifecycleVersion(snapshot.publishedVersion()) : null,
                toLifecycleVersion(snapshot.activeVersion()),
                "REVIEW_TASK"
        );

        List<SkillVersionResponse> versions = snapshot.versions().stream()
                .map(version -> new SkillVersionResponse(
                        version.getId(),
                        version.getVersion(),
                        version.getStatus().name(),
                        version.getChangelog(),
                        version.getFileCount(),
                        version.getTotalSize(),
                        version.getPublishedAt(),
                        version.getId().equals(snapshot.activeVersion().getId())
                                || skillQueryService.isDownloadAvailable(version)
                ))
                .toList();

        List<SkillFileResponse> files = snapshot.files().stream()
                .map(this::toFileResponse)
                .toList();

        return new ReviewSkillDetailResponse(
                skill,
                versions,
                files,
                snapshot.documentationPath(),
                snapshot.documentationContent(),
                "/api/v1/reviews/" + reviewId + "/download",
                snapshot.activeVersion().getVersion()
        );
    }

    public SkillDownloadService.DownloadResult downloadReviewPackage(Long reviewId,
                                                                    String userId,
                                                                    Map<Long, NamespaceRole> userNsRoles) {
        ReviewAccessContext context = loadAuthorizedContext(reviewId, userId, userNsRoles);
        SkillQueryService.ReviewSkillSnapshotDTO snapshot =
                skillQueryService.getReviewSkillSnapshot(context.task().getSkillVersionId());
        return skillDownloadService.downloadReviewVersion(snapshot.skill(), snapshot.activeVersion());
    }

    private ReviewAccessContext loadAuthorizedContext(Long reviewId,
                                                      String userId,
                                                      Map<Long, NamespaceRole> userNsRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewId));
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        Map<Long, NamespaceRole> namespaceRoles = userNsRoles != null ? userNsRoles : Map.of();
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        if (!reviewService.canViewReview(task, userId, namespace.getType(), namespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }
        return new ReviewAccessContext(task, namespace);
    }

    private SkillFileResponse toFileResponse(SkillFile file) {
        return new SkillFileResponse(
                file.getId(),
                file.getFilePath(),
                file.getFileSize(),
                file.getContentType(),
                file.getSha256()
        );
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillVersion version) {
        return new SkillLifecycleVersionResponse(version.getId(), version.getVersion(), version.getStatus().name());
    }

    private record ReviewAccessContext(ReviewTask task, Namespace namespace) {}
}
