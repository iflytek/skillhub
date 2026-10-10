package com.iflytek.skillhub.domain.user;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileReviewServiceTest {

    @Mock
    private ProfileChangeRequestRepository changeRequestRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AuditLogService auditLogService;

    private ProfileReviewService service;

    @BeforeEach
    void setUp() {
        service = new ProfileReviewService(changeRequestRepository, userAccountRepository, auditLogService);
    }

    private ProfileChangeRequest pendingRequest(String userId) {
        var req = new ProfileChangeRequest(userId,
                "{\"displayName\":\"NewName\"}", "{\"displayName\":\"OldName\"}",
                ProfileChangeStatus.PENDING, "PASS", null);
        // Use reflection to set id since JPA normally handles it
        try {
            var idField = ProfileChangeRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(req, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return req;
    }

    // ===== AC-AR-P-002: approve success =====

    @Test
    void approve_success_appliesChangeAndSetsApproved() {
        var request = pendingRequest("user-1");
        var user = new UserAccount("user-1", "OldName", "u@example.com", null);

        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));

        var result = service.approve(1L, "admin-1", "req-1", "127.0.0.1", "TestAgent");

        assertEquals(ProfileChangeStatus.APPROVED, result.getStatus());
        assertEquals("admin-1", result.getReviewerId());
        assertNotNull(result.getReviewedAt());
        assertEquals("NewName", user.getDisplayName());
        verify(userAccountRepository).save(user);
        verify(changeRequestRepository).save(request);
        verify(auditLogService).record(eq("admin-1"), eq("PROFILE_REVIEW_APPROVE"),
                eq("PROFILE_CHANGE_REQUEST"), eq(1L), any(), any(), any(), any());
    }

    // ===== AC-AR-P-003: reject success =====

    @Test
    void reject_success_setsRejectedWithComment() {
        var request = pendingRequest("user-1");
        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        var result = service.reject(1L, "admin-1", "Not appropriate", "req-1", "127.0.0.1", "TestAgent");

        assertEquals(ProfileChangeStatus.REJECTED, result.getStatus());
        assertEquals("admin-1", result.getReviewerId());
        assertEquals("Not appropriate", result.getReviewComment());
        assertNotNull(result.getReviewedAt());
        verify(userAccountRepository, never()).save(any());
        verify(auditLogService).record(eq("admin-1"), eq("PROFILE_REVIEW_REJECT"),
                eq("PROFILE_CHANGE_REQUEST"), eq(1L), any(), any(), any(), any());
    }

    // ===== AC-AR-E-001: approve non-PENDING throws =====

    @Test
    void approve_nonPending_throwsBadRequest() {
        var request = pendingRequest("user-1");
        request.setStatus(ProfileChangeStatus.APPROVED);
        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(DomainBadRequestException.class,
                () -> service.approve(1L, "admin-1", "req-1", "127.0.0.1", "TestAgent"));
    }

    // ===== AC-AR-E-003: not found throws 404 =====

    @Test
    void approve_notFound_throwsNotFoundException() {
        when(changeRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.approve(99L, "admin-1", "req-1", "127.0.0.1", "TestAgent"));
    }

    // ===== AC-AR-E-004: approve when user not found =====

    @Test
    void approve_userNotFound_throwsNotFoundException() {
        var request = pendingRequest("deleted-user");
        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById("deleted-user")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.approve(1L, "admin-1", "req-1", "127.0.0.1", "TestAgent"));
    }
}
