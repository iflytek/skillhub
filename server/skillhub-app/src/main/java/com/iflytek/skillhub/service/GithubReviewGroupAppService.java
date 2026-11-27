package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.GithubReviewGroupResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.GithubReviewGroupMemberResponse;
import com.iflytek.skillhub.repository.GithubReviewGroupQueryRepository;
import com.iflytek.skillhub.repository.ReviewQueueQueryRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class GithubReviewGroupAppService {
    private final ReviewPortalAppService reviews;
    private final GithubReviewGroupQueryRepository groups;

    public GithubReviewGroupAppService(ReviewPortalAppService reviews, GithubReviewGroupQueryRepository groups) {
        this.reviews = reviews; this.groups = groups;
    }

    private String filterValue(String value, Set<String> allowed) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalized)) throw new DomainBadRequestException("error.review.queue.invalidFilter");
        return normalized;
    }

    private ReviewQueueQueryRepository.Filter filter(String status, Long namespaceId, String q, String kind,
            int page, int size, String direction) {
        if (page < 0 || size < 1 || size > 50 || q != null && q.length() > 200
                || !("ASC".equals(direction) || "DESC".equals(direction)))
            throw new DomainBadRequestException("error.review.queue.invalidFilter");
        return new ReviewQueueQueryRepository.Filter(filterValue(status, Set.of("PENDING", "APPROVED", "REJECTED", "SUPERSEDED")),
                namespaceId, q == null ? "" : q, filterValue(kind, Set.of("FIRST_PUBLISH", "VERSION_UPDATE",
                        "SUITE_PUBLISH", "VISIBILITY_CHANGE", "AVATAR_CHANGE")), "GITHUB_SYNC");
    }

    private Set<Long> scopes(String actor, Map<Long, NamespaceRole> roles, Long namespaceId) {
        Set<Long> scopes = reviews.listReviewScopes(actor, roles).stream().map(scope -> scope.id()).collect(Collectors.toSet());
        if (namespaceId != null && !scopes.contains(namespaceId)) throw new DomainForbiddenException("review.no_permission");
        return scopes;
    }

    public PageResponse<GithubReviewGroupResponse> list(String status, Long namespaceId, String q, String kind,
            int page, int size, String direction, String actor, Map<Long, NamespaceRole> roles) {
        var filter = filter(status, namespaceId, q, kind, page, size, direction);
        return groups.list(filter, scopes(actor, roles, namespaceId), page, size, "ASC".equals(direction));
    }

    public GithubReviewGroupResponse describe(long snapshotId, String actor, Map<Long, NamespaceRole> roles) {
        if (snapshotId <= 0) throw new DomainBadRequestException("error.review.queue.invalidFilter");
        return groups.list(new ReviewQueueQueryRepository.Filter(null, null, "", null, "GITHUB_SYNC"),
                scopes(actor, roles, null), 0, 1, false, snapshotId).items().stream().findFirst()
                .orElseThrow(() -> new com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException("review_task.not_found", snapshotId));
    }

    public PageResponse<GithubReviewGroupMemberResponse> members(long snapshotId, String status, Long namespaceId, String q,
            String kind, int page, int size, String direction, String actor, Map<Long, NamespaceRole> roles) {
        if (snapshotId <= 0) throw new DomainBadRequestException("error.review.queue.invalidFilter");
        var filter = filter(status, namespaceId, q, kind, page, size, direction);
        var ids = groups.members(filter, scopes(actor, roles, namespaceId), snapshotId, page, size, "ASC".equals(direction));
        // Recheck each task's current permission through the existing detail boundary.
        var tasks = ids.items().stream().map(member -> new GithubReviewGroupMemberResponse(reviews.getReviewDetail(member.reviewId(), actor, roles).withPresentation(member.displayName(), member.summary(), member.reviewKind(), null), member.scanStatus())).toList();
        return new PageResponse<>(tasks, ids.total(), ids.page(), ids.size());
    }
}
