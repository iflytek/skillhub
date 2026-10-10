package com.iflytek.skillhub.domain.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Domain service for admin review of profile change requests.
 *
 * <p>Handles the approve/reject workflow: validates the request state,
 * applies changes to {@code user_account} on approval, and records
 * audit logs for all review actions.
 */
@Service
public class ProfileReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ProfileChangeRequestRepository changeRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;

    public ProfileReviewService(ProfileChangeRequestRepository changeRequestRepository,
                                UserAccountRepository userAccountRepository,
                                AuditLogService auditLogService) {
        this.changeRequestRepository = changeRequestRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
    }

    /** List change requests by status with pagination. */
    @Transactional(readOnly = true)
    public Page<ProfileChangeRequest> listByStatus(ProfileChangeStatus status, Pageable pageable, String sortDirection) {
        String primaryField = status == ProfileChangeStatus.PENDING ? "createdAt" : "reviewedAt";
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection).orElse(Sort.Direction.DESC);
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        new Sort.Order(direction, primaryField),
                        new Sort.Order(direction, "id")
                )
        );
        return changeRequestRepository.findByStatus(status, sortedPageable);
    }

    /**
     * Approve a PENDING profile change request.
     *
     * <p>Applies the requested field changes to the user account,
     * transitions the request to APPROVED, and records an audit log.
     *
     * @param requestId  the change request ID
     * @param reviewerId admin user performing the review
     * @param httpReqId  HTTP request ID for audit trail
     * @param clientIp   reviewer's IP address
     * @param userAgent  reviewer's user agent
     * @return the updated change request
     * @throws DomainNotFoundException if the request does not exist
     * @throws DomainBadRequestException if the request is not in PENDING status
     */
    @Transactional
    public ProfileChangeRequest approve(Long requestId, String reviewerId,
                                        String httpReqId, String clientIp, String userAgent) {
        var request = findPendingOrThrow(requestId);

        // Apply field changes to user account
        var user = userAccountRepository.findById(request.getUserId())
                .orElseThrow(() -> new DomainNotFoundException("error.user.notFound"));
        applyChanges(user, parseJson(request.getChanges()));
        userAccountRepository.save(user);

        // Transition request state
        request.setStatus(ProfileChangeStatus.APPROVED);
        request.setReviewerId(reviewerId);
        request.setReviewedAt(Instant.now());
        changeRequestRepository.save(request);

        auditLogService.record(reviewerId, "PROFILE_REVIEW_APPROVE", "PROFILE_CHANGE_REQUEST",
                requestId, httpReqId, clientIp, userAgent, null);

        return request;
    }

    /**
     * Reject a PENDING profile change request with a reason.
     *
     * @param requestId  the change request ID
     * @param reviewerId admin user performing the review
     * @param comment    rejection reason shown to the user
     * @param httpReqId  HTTP request ID for audit trail
     * @param clientIp   reviewer's IP address
     * @param userAgent  reviewer's user agent
     * @return the updated change request
     */
    @Transactional
    public ProfileChangeRequest reject(Long requestId, String reviewerId, String comment,
                                       String httpReqId, String clientIp, String userAgent) {
        var request = findPendingOrThrow(requestId);

        request.setStatus(ProfileChangeStatus.REJECTED);
        request.setReviewerId(reviewerId);
        request.setReviewedAt(Instant.now());
        request.setReviewComment(comment);
        changeRequestRepository.save(request);

        auditLogService.record(reviewerId, "PROFILE_REVIEW_REJECT", "PROFILE_CHANGE_REQUEST",
                requestId, httpReqId, clientIp, userAgent,
                toJson(Map.of("comment", comment)));

        return request;
    }

    // ── Private helpers ──────────────────────────────────────────────

    private ProfileChangeRequest findPendingOrThrow(Long requestId) {
        var request = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new DomainNotFoundException("error.profileReview.notFound"));
        if (request.getStatus() != ProfileChangeStatus.PENDING) {
            throw new DomainBadRequestException("error.profileReview.notPending");
        }
        return request;
    }

    private void applyChanges(UserAccount user, Map<String, String> changes) {
        if (changes.containsKey("displayName")) {
            user.setDisplayName(changes.get("displayName"));
        }
        // Future: avatarUrl, bio, etc.
    }

    private Map<String, String> parseJson(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt changes JSON in change request", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }
}
