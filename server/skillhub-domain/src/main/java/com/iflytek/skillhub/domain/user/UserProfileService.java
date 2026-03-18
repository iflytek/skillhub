package com.iflytek.skillhub.domain.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core service for user profile updates.
 *
 * <p>Orchestrates the full update flow: validation → machine review →
 * human review (if configured) → apply changes → audit logging.
 *
 * <p>The moderation behavior is driven by {@link ProfileModerationConfig}:
 * when both switches are off, changes apply immediately (open-source default).
 */
@Service
public class UserProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserAccountRepository userAccountRepository;
    private final ProfileChangeRequestRepository changeRequestRepository;
    private final ProfileModerationService moderationService;
    private final ProfileModerationConfig moderationConfig;
    private final AuditLogService auditLogService;

    public UserProfileService(UserAccountRepository userAccountRepository,
                               ProfileChangeRequestRepository changeRequestRepository,
                               ProfileModerationService moderationService,
                               ProfileModerationConfig moderationConfig,
                               AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.moderationService = moderationService;
        this.moderationConfig = moderationConfig;
        this.auditLogService = auditLogService;
    }

    /**
     * Update user profile fields (e.g. displayName, avatarUrl).
     *
     * <p>Depending on moderation config, this may:
     * <ul>
     *   <li>Apply changes immediately (no moderation)</li>
     *   <li>Reject via machine review</li>
     *   <li>Queue for human review (PENDING)</li>
     * </ul>
     *
     * @param userId    the user making the change
     * @param changes   map of field name → new value
     * @param requestId HTTP request ID for audit trail
     * @param clientIp  client IP address
     * @param userAgent client user agent
     * @return result indicating whether changes were applied or queued
     */
    @Transactional
    public UpdateProfileResult updateProfile(String userId,
                                              Map<String, String> changes,
                                              String requestId,
                                              String clientIp,
                                              String userAgent) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 1. Build snapshot of old values for audit and rollback
        Map<String, String> oldValues = buildOldValues(user, changes);

        // 2. Machine review (if enabled)
        if (moderationConfig.machineReview()) {
            ModerationResult machineResult = moderationService.moderate(userId, changes);
            if (machineResult.decision() == ModerationDecision.REJECTED) {
                saveChangeRequest(userId, changes, oldValues, ProfileChangeStatus.MACHINE_REJECTED,
                                  "FAIL", machineResult.reason());
                throw new IllegalArgumentException(machineResult.reason());
            }
        }

        // 3. Human review (if enabled)
        if (moderationConfig.humanReview()) {
            cancelPendingRequests(userId);  // Replace any existing PENDING request
            saveChangeRequest(userId, changes, oldValues, ProfileChangeStatus.PENDING,
                              moderationConfig.machineReview() ? "PASS" : "SKIPPED", null);
            return UpdateProfileResult.pendingReview();
        }

        // 4. No moderation — apply changes immediately
        applyChanges(user, changes);
        saveChangeRequest(userId, changes, oldValues, ProfileChangeStatus.APPROVED,
                          moderationConfig.machineReview() ? "PASS" : "SKIPPED", null);

        // 5. Audit log
        auditLogService.record(userId, "PROFILE_UPDATE", "USER", null,
                               requestId, clientIp, userAgent,
                               toJson(Map.of("changes", changes, "oldValues", oldValues)));

        return UpdateProfileResult.applied();
    }

    /**
     * Apply approved changes to the user account.
     * Extensible for future fields (avatarUrl, etc.).
     */
    private void applyChanges(UserAccount user, Map<String, String> changes) {
        if (changes.containsKey("displayName")) {
            user.setDisplayName(changes.get("displayName"));
        }
        // Future: avatarUrl, etc.
        userAccountRepository.save(user);
    }

    /**
     * Cancel any existing PENDING requests for this user.
     * Called when a user submits a new change, superseding the old one.
     */
    private void cancelPendingRequests(String userId) {
        changeRequestRepository.findByUserIdAndStatus(userId, ProfileChangeStatus.PENDING)
                .forEach(req -> {
                    req.setStatus(ProfileChangeStatus.CANCELLED);
                    changeRequestRepository.save(req);
                });
    }

    /**
     * Build a snapshot of the current values for fields being changed.
     * Used for audit trail and potential rollback.
     */
    private Map<String, String> buildOldValues(UserAccount user, Map<String, String> changes) {
        Map<String, String> oldValues = new LinkedHashMap<>();
        if (changes.containsKey("displayName")) {
            oldValues.put("displayName", user.getDisplayName());
        }
        // Future: avatarUrl, etc.
        return oldValues;
    }

    /**
     * Persist a change request record for audit and review purposes.
     */
    private void saveChangeRequest(String userId, Map<String, String> changes,
                                     Map<String, String> oldValues, ProfileChangeStatus status,
                                     String machineResult, String machineReason) {
        ProfileChangeRequest request = new ProfileChangeRequest(
                userId,
                toJson(changes),
                toJson(oldValues),
                status,
                machineResult,
                machineReason
        );
        changeRequestRepository.save(request);
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
