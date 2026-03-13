package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController extends BaseApiController {

    private final ReviewService reviewService;
    private final ReviewTaskRepository reviewTaskRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;

    public ReviewController(ReviewService reviewService,
                            ReviewTaskRepository reviewTaskRepository,
                            SkillRepository skillRepository,
                            SkillVersionRepository skillVersionRepository,
                            NamespaceRepository namespaceRepository,
                            UserAccountRepository userAccountRepository,
                            RbacService rbacService,
                            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.reviewService = reviewService;
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ApiResponse<ReviewTaskResponse> submitReview(
            @RequestBody ReviewTaskRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        ReviewTask task = reviewService.submitReview(
                request.skillVersionId(),
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                rbacService.getUserRoleCodes(userId)
        );
        return ok("response.success.created", toResponse(task));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ReviewTaskResponse> approveReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        String comment = request != null ? request.comment() : null;
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        ReviewTask task = reviewService.approveReview(id, userId, comment,
                userNsRoles != null ? userNsRoles : Map.of(), platformRoles);
        return ok("response.success.updated", toResponse(task));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ReviewTaskResponse> rejectReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        String comment = request != null ? request.comment() : null;
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        ReviewTask task = reviewService.rejectReview(id, userId, comment,
                userNsRoles != null ? userNsRoles : Map.of(), platformRoles);
        return ok("response.success.updated", toResponse(task));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdrawReview(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId) {
        ReviewTask task = reviewTaskRepository.findById(id).orElseThrow();
        reviewService.withdrawReview(task.getSkillVersionId(), userId);
        return ok("response.success.updated", null);
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<ReviewTaskResponse>> listPendingReviews(
            @RequestParam Long namespaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceId));
        ReviewTask probe = new ReviewTask(0L, namespaceId, "probe");
        if (!reviewService.canReviewNamespace(probe, userId, namespace.getType(),
                userNsRoles != null ? userNsRoles : Map.of(), rbacService.getUserRoleCodes(userId))) {
            throw new DomainForbiddenException("review.no_permission");
        }
        Page<ReviewTask> tasks = reviewTaskRepository.findByNamespaceIdAndStatus(
                namespaceId, ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return ok("response.success.read", PageResponse.from(tasks.map(this::toResponse)));
    }

    @GetMapping("/my-submissions")
    public ApiResponse<PageResponse<ReviewTaskResponse>> listMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId) {
        Page<ReviewTask> tasks = reviewTaskRepository.findBySubmittedByAndStatus(
                userId, ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return ok("response.success.read", PageResponse.from(tasks.map(this::toResponse)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewTaskResponse> getReviewDetail(@PathVariable Long id,
                                                           @RequestAttribute("userId") String userId,
                                                           @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", id));
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        if (!reviewService.canViewReview(task, userId, namespace.getType(),
                userNsRoles != null ? userNsRoles : Map.of(), rbacService.getUserRoleCodes(userId))) {
            throw new DomainForbiddenException("review.no_permission");
        }
        return ok("response.success.read", toResponse(task));
    }

    private ReviewTaskResponse toResponse(ReviewTask task) {
        SkillVersion sv = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        Skill skill = skillRepository.findById(sv.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sv.getSkillId()));
        Namespace ns = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));

        String submittedByName = userAccountRepository.findById(task.getSubmittedBy())
                .map(UserAccount::getDisplayName).orElse(null);

        String reviewedByName = task.getReviewedBy() != null
                ? userAccountRepository.findById(task.getReviewedBy())
                        .map(UserAccount::getDisplayName).orElse(null)
                : null;

        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                ns.getSlug(),
                skill.getSlug(),
                sv.getVersion(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                submittedByName,
                task.getReviewedBy(),
                reviewedByName,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }
}
