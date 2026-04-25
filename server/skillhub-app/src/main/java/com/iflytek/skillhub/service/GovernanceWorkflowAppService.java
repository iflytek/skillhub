package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.dto.SkillVersionRereleaseRequest;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Workflow-facing application facade for moderation and lifecycle actions.
 *
 * <p>This service provides one explicit application-layer owner for namespace
 * governance, skill lifecycle mutations, review workflow operations, and
 * promotion workflow operations while delegating the detailed behavior to the
 * existing app services.
 */
@Service
public class GovernanceWorkflowAppService {

    private final ReviewPortalAppService reviewPortalAppService;
    private final ReviewSkillDetailAppService reviewSkillDetailAppService;
    private final PromotionPortalAppService promotionPortalAppService;
    private final SkillLifecycleAppService skillLifecycleAppService;
    private final NamespacePortalCommandAppService namespacePortalCommandAppService;

    public GovernanceWorkflowAppService(ReviewPortalAppService reviewPortalAppService,
                                        ReviewSkillDetailAppService reviewSkillDetailAppService,
                                        PromotionPortalAppService promotionPortalAppService,
                                        SkillLifecycleAppService skillLifecycleAppService,
                                        NamespacePortalCommandAppService namespacePortalCommandAppService) {
        this.reviewPortalAppService = reviewPortalAppService;
        this.reviewSkillDetailAppService = reviewSkillDetailAppService;
        this.promotionPortalAppService = promotionPortalAppService;
        this.skillLifecycleAppService = skillLifecycleAppService;
        this.namespacePortalCommandAppService = namespacePortalCommandAppService;
    }

    public ReviewTaskResponse submitReview(Long skillVersionId,
                                           String userId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           AuditRequestContext auditContext) {
        return reviewPortalAppService.submitReview(skillVersionId, userId, userNsRoles, auditContext);
    }

    public ReviewTaskResponse approveReview(Long reviewTaskId,
                                            String comment,
                                            String userId,
                                            Map<Long, NamespaceRole> userNsRoles,
                                            AuditRequestContext auditContext) {
        return reviewPortalAppService.approveReview(reviewTaskId, comment, userId, userNsRoles, auditContext);
    }

    public ReviewTaskResponse rejectReview(Long reviewTaskId,
                                           String comment,
                                           String userId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           AuditRequestContext auditContext) {
        return reviewPortalAppService.rejectReview(reviewTaskId, comment, userId, userNsRoles, auditContext);
    }

    public void withdrawReviewTask(Long reviewTaskId, String userId, AuditRequestContext auditContext) {
        reviewPortalAppService.withdrawReview(reviewTaskId, userId, auditContext);
    }

    public PageResponse<ReviewTaskResponse> listReviews(String status,
                                                        Long namespaceId,
                                                        int page,
                                                        int size,
                                                        String sortDirection,
                                                        String userId,
                                                        Map<Long, NamespaceRole> userNsRoles) {
        return reviewPortalAppService.listReviews(status, namespaceId, page, size, sortDirection, userId, userNsRoles);
    }

    public PageResponse<ReviewTaskResponse> listPendingReviews(Long namespaceId,
                                                               int page,
                                                               int size,
                                                               String userId,
                                                               Map<Long, NamespaceRole> userNsRoles) {
        return reviewPortalAppService.listPendingReviews(namespaceId, page, size, userId, userNsRoles);
    }

    public PageResponse<ReviewTaskResponse> listMyReviewSubmissions(int page, int size, String userId) {
        return reviewPortalAppService.listMySubmissions(page, size, userId);
    }

    public ReviewTaskResponse getReviewDetail(Long reviewTaskId,
                                              String userId,
                                              Map<Long, NamespaceRole> userNsRoles) {
        return reviewPortalAppService.getReviewDetail(reviewTaskId, userId, userNsRoles);
    }

    public ReviewSkillDetailResponse getReviewSkillDetail(Long reviewTaskId,
                                                          String userId,
                                                          Map<Long, NamespaceRole> userNsRoles) {
        return reviewSkillDetailAppService.getReviewSkillDetail(
                reviewTaskId,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
    }

    public SkillDownloadService.DownloadResult downloadReviewPackage(Long reviewTaskId,
                                                                     String userId,
                                                                     Map<Long, NamespaceRole> userNsRoles) {
        return reviewSkillDetailAppService.downloadReviewPackage(
                reviewTaskId,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
    }

    /**
     * Reads a single file from the review-bound skill version.
     * Delegates to ReviewSkillDetailAppService for authorization and file access.
     */
    public InputStream getReviewFileContent(Long reviewTaskId,
                                            String filePath,
                                            String userId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        return reviewSkillDetailAppService.getReviewFileContent(
                reviewTaskId,
                filePath,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
    }

    public PromotionResponseDto submitPromotion(Long sourceSkillId,
                                                Long sourceVersionId,
                                                Long targetNamespaceId,
                                                String userId,
                                                Map<Long, NamespaceRole> userNsRoles,
                                                AuditRequestContext auditContext) {
        return promotionPortalAppService.submitPromotion(
                sourceSkillId,
                sourceVersionId,
                targetNamespaceId,
                userId,
                userNsRoles,
                auditContext
        );
    }

    public PromotionResponseDto approvePromotion(Long promotionId,
                                                 String comment,
                                                 String userId,
                                                 AuditRequestContext auditContext) {
        return promotionPortalAppService.approvePromotion(promotionId, comment, userId, auditContext);
    }

    public PromotionResponseDto rejectPromotion(Long promotionId,
                                                String comment,
                                                String userId,
                                                AuditRequestContext auditContext) {
        return promotionPortalAppService.rejectPromotion(promotionId, comment, userId, auditContext);
    }

    public PageResponse<PromotionResponseDto> listPromotions(String status, int page, int size, String userId) {
        return promotionPortalAppService.listPromotions(status, page, size, userId);
    }

    public PageResponse<PromotionResponseDto> listPendingPromotions(int page, int size, String userId) {
        return promotionPortalAppService.listPendingPromotions(page, size, userId);
    }

    public PromotionResponseDto getPromotionDetail(Long promotionId, String userId) {
        return promotionPortalAppService.getPromotionDetail(promotionId, userId);
    }

    public SkillLifecycleMutationResponse archiveSkill(String namespace,
                                                       String slug,
                                                       AdminSkillActionRequest request,
                                                       String userId,
                                                       Map<Long, NamespaceRole> userNsRoles,
                                                       Set<String> platformRoles,
                                                       AuditRequestContext auditContext) {
        return skillLifecycleAppService.archiveSkill(namespace, slug, request, userId, userNsRoles, platformRoles, auditContext);
    }

    public SkillLifecycleMutationResponse unarchiveSkill(String namespace,
                                                         String slug,
                                                         String userId,
                                                         Map<Long, NamespaceRole> userNsRoles,
                                                         Set<String> platformRoles,
                                                         AuditRequestContext auditContext) {
        return skillLifecycleAppService.unarchiveSkill(namespace, slug, userId, userNsRoles, platformRoles, auditContext);
    }

    public SkillLifecycleMutationResponse deleteVersion(String namespace,
                                                        String slug,
                                                        String version,
                                                        String userId,
                                                        Map<Long, NamespaceRole> userNsRoles,
                                                        Set<String> platformRoles,
                                                        AuditRequestContext auditContext) {
        return skillLifecycleAppService.deleteVersion(namespace, slug, version, userId, userNsRoles, platformRoles, auditContext);
    }

    public SkillLifecycleMutationResponse withdrawReviewVersion(String namespace,
                                                                String slug,
                                                                String version,
                                                                String userId,
                                                                AuditRequestContext auditContext) {
        return skillLifecycleAppService.withdrawReview(namespace, slug, version, userId, auditContext);
    }

    public SkillLifecycleMutationResponse rereleaseVersion(String namespace,
                                                           String slug,
                                                           String version,
                                                           SkillVersionRereleaseRequest request,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNsRoles,
                                                           AuditRequestContext auditContext) {
        return skillLifecycleAppService.rereleaseVersion(
                namespace,
                slug,
                version,
                request,
                userId,
                userNsRoles,
                auditContext
        );
    }

    public NamespaceResponse freezeNamespace(String slug,
                                             NamespaceLifecycleRequest request,
                                             String userId,
                                             AuditRequestContext auditContext) {
        return namespacePortalCommandAppService.freezeNamespace(slug, request, userId, auditContext);
    }

    public NamespaceResponse unfreezeNamespace(String slug,
                                               String userId,
                                               AuditRequestContext auditContext) {
        return namespacePortalCommandAppService.unfreezeNamespace(slug, userId, auditContext);
    }

    public NamespaceResponse archiveNamespace(String slug,
                                              NamespaceLifecycleRequest request,
                                              String userId,
                                              AuditRequestContext auditContext) {
        return namespacePortalCommandAppService.archiveNamespace(slug, request, userId, auditContext);
    }

    public NamespaceResponse restoreNamespace(String slug,
                                              String userId,
                                              AuditRequestContext auditContext) {
        return namespacePortalCommandAppService.restoreNamespace(slug, userId, auditContext);
    }

    public SkillLifecycleMutationResponse submitForReview(String namespace,
                                                           String slug,
                                                           String version,
                                                           String targetVisibility,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNsRoles,
                                                           AuditRequestContext auditContext) {
        return skillLifecycleAppService.submitForReview(
                namespace,
                slug,
                version,
                targetVisibility,
                userId,
                userNsRoles,
                auditContext);
    }

    public SkillLifecycleMutationResponse confirmPublish(String namespace,
                                                           String slug,
                                                           String version,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNsRoles,
                                                           AuditRequestContext auditContext) {
        return skillLifecycleAppService.confirmPublish(
                namespace,
                slug,
                version,
                userId,
                userNsRoles,
                auditContext);
    }

    public SkillLifecycleMutationResponse hideSkill(String namespace,
                                                    String slug,
                                                    AdminSkillActionRequest request,
                                                    String userId,
                                                    Map<Long, NamespaceRole> userNsRoles,
                                                    Set<String> platformRoles,
                                                    AuditRequestContext auditContext) {
        return skillLifecycleAppService.hideSkill(namespace, slug, request, userId, userNsRoles, platformRoles, auditContext);
    }

    public SkillLifecycleMutationResponse unhideSkill(String namespace,
                                                      String slug,
                                                      String userId,
                                                      Map<Long, NamespaceRole> userNsRoles,
                                                      Set<String> platformRoles,
                                                      AuditRequestContext auditContext) {
        return skillLifecycleAppService.unhideSkill(namespace, slug, userId, userNsRoles, platformRoles, auditContext);
    }
}
