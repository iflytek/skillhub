package com.iflytek.skillhub.domain.user;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserProfileService}.
 * Covers the core update flow under different moderation configurations.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ProfileChangeRequestRepository changeRequestRepository;

    @Mock
    private ProfileModerationService moderationService;

    @Mock
    private ProfileModerationConfig moderationConfig;

    @Mock
    private ProfileFieldPolicyConfig fieldPolicyConfig;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserProfileService userProfileService;

    // -- Helper --

    private UserAccount testUser() {
        return new UserAccount("user-1", "OldName", "user@example.com", "https://example.com/avatar.png");
    }

    private Map<String, String> displayNameChange(String newName) {
        return Map.of("displayName", newName);
    }

    private void stubFieldPolicies(boolean requiresReview) {
        when(fieldPolicyConfig.fieldPolicies()).thenReturn(Map.of(
                "displayName", new ProfileFieldPolicyConfig.FieldPolicy(true, requiresReview)));
    }

    // ===== AC-P-001: Successful update with no moderation =====

    @Test
    void updateProfile_noModeration_shouldApplyImmediately() {
        var user = testUser();
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(false);
        stubFieldPolicies(false);

        var result = userProfileService.updateProfile(
                "user-1", displayNameChange("NewName"), "req-1", "127.0.0.1", "TestAgent");

        // Should return Applied
        assertInstanceOf(UpdateProfileResult.Applied.class, result);

        // user_account should be updated
        assertEquals("NewName", user.getDisplayName());
        verify(userAccountRepository).save(user);

        // Change request should be saved as APPROVED
        var captor = ArgumentCaptor.forClass(ProfileChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertEquals(ProfileChangeStatus.APPROVED, captor.getValue().getStatus());

        // Audit log should be recorded
        verify(auditLogService).record(eq("user-1"), eq("PROFILE_UPDATE"),
                eq("USER"), isNull(), eq("req-1"), eq("127.0.0.1"), eq("TestAgent"), any());
    }

    // ===== AC-P-003: Same value (idempotent) =====

    @Test
    void updateProfile_sameValue_shouldSucceed() {
        var user = testUser();
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(false);
        stubFieldPolicies(false);

        var result = userProfileService.updateProfile(
                "user-1", displayNameChange("OldName"), "req-1", "127.0.0.1", "TestAgent");

        assertInstanceOf(UpdateProfileResult.Applied.class, result);
        assertEquals("OldName", user.getDisplayName());
    }

    // ===== AC-P-004: Human review enabled — creates PENDING request =====

    @Test
    void updateProfile_humanReviewEnabled_shouldCreatePendingRequest() {
        var user = testUser();
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(false);
        when(moderationConfig.humanReview()).thenReturn(true);
        stubFieldPolicies(true);
        when(changeRequestRepository.findByUserIdAndStatus("user-1", ProfileChangeStatus.PENDING))
                .thenReturn(List.of());

        var result = userProfileService.updateProfile(
                "user-1", displayNameChange("NewName"), "req-1", "127.0.0.1", "TestAgent");

        // Should return PendingReview
        assertInstanceOf(UpdateProfileResult.PendingReview.class, result);

        // user_account should NOT be updated
        assertEquals("OldName", user.getDisplayName());
        verify(userAccountRepository, never()).save(any());

        // Change request should be saved as PENDING
        var captor = ArgumentCaptor.forClass(ProfileChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertEquals(ProfileChangeStatus.PENDING, captor.getValue().getStatus());
        assertEquals("SKIPPED", captor.getValue().getMachineResult());

        // No audit log for pending requests
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ===== AC-P-005: Overwrite existing PENDING request =====

    @Test
    void updateProfile_existingPending_shouldCancelOldAndCreateNew() {
        var user = testUser();
        var oldRequest = new ProfileChangeRequest("user-1", "{\"displayName\":\"PendingName\"}",
                "{\"displayName\":\"OldName\"}", ProfileChangeStatus.PENDING, "SKIPPED", null);

        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(false);
        when(moderationConfig.humanReview()).thenReturn(true);
        stubFieldPolicies(true);
        when(changeRequestRepository.findByUserIdAndStatus("user-1", ProfileChangeStatus.PENDING))
                .thenReturn(List.of(oldRequest));

        userProfileService.updateProfile(
                "user-1", displayNameChange("NewerName"), "req-1", "127.0.0.1", "TestAgent");

        // Old request should be cancelled
        assertEquals(ProfileChangeStatus.CANCELLED, oldRequest.getStatus());

        // Two saves: one for cancel, one for new request
        verify(changeRequestRepository, times(2)).save(any(ProfileChangeRequest.class));
    }

    // ===== Machine review: pass then human review =====

    @Test
    void updateProfile_machinePassAndHumanReview_shouldCreatePendingWithPassResult() {
        var user = testUser();
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(true);
        when(moderationConfig.humanReview()).thenReturn(true);
        when(moderationService.moderate("user-1", displayNameChange("NewName")))
                .thenReturn(ModerationResult.approved());
        stubFieldPolicies(true);
        when(changeRequestRepository.findByUserIdAndStatus("user-1", ProfileChangeStatus.PENDING))
                .thenReturn(List.of());

        var result = userProfileService.updateProfile(
                "user-1", displayNameChange("NewName"), "req-1", "127.0.0.1", "TestAgent");

        assertInstanceOf(UpdateProfileResult.PendingReview.class, result);

        var captor = ArgumentCaptor.forClass(ProfileChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertEquals("PASS", captor.getValue().getMachineResult());
    }

    // ===== Machine review: rejected =====

    @Test
    void updateProfile_machineRejected_shouldThrowAndSaveRejection() {
        var user = testUser();
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(moderationConfig.machineReview()).thenReturn(true);
        when(moderationService.moderate("user-1", displayNameChange("BadWord")))
                .thenReturn(ModerationResult.rejected("Contains sensitive content"));

        var ex = assertThrows(IllegalArgumentException.class, () ->
                userProfileService.updateProfile(
                        "user-1", displayNameChange("BadWord"), "req-1", "127.0.0.1", "TestAgent"));

        assertEquals("Contains sensitive content", ex.getMessage());

        // Change request should be saved as MACHINE_REJECTED
        var captor = ArgumentCaptor.forClass(ProfileChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertEquals(ProfileChangeStatus.MACHINE_REJECTED, captor.getValue().getStatus());
        assertEquals("FAIL", captor.getValue().getMachineResult());

        // user_account should NOT be updated
        verify(userAccountRepository, never()).save(any());
    }

    // ===== User not found =====

    @Test
    void updateProfile_userNotFound_shouldThrow() {
        when(userAccountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                userProfileService.updateProfile(
                        "nonexistent", displayNameChange("Name"), "req-1", "127.0.0.1", "TestAgent"));
    }
}
