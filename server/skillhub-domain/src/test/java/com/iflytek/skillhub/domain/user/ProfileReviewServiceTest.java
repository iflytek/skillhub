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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
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

    // ===== listByStatus =====

    @Test
    void listByStatus_pending_sortsByCreatedAtDesc() {
        Pageable pageable = PageRequest.of(0, 10);
        var request = pendingRequest("user-1");
        Page<ProfileChangeRequest> page = new PageImpl<>(java.util.List.of(request));
        when(changeRequestRepository.findByStatus(ProfileChangeStatus.PENDING,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))))
                .thenReturn(page);

        Page<ProfileChangeRequest> result = service.listByStatus(ProfileChangeStatus.PENDING, pageable, "DESC");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listByStatus_nonPending_sortsByReviewedAtAsc() {
        Pageable pageable = PageRequest.of(0, 10);
        var request = pendingRequest("user-1");
        request.setStatus(ProfileChangeStatus.APPROVED);
        Page<ProfileChangeRequest> page = new PageImpl<>(java.util.List.of(request));
        when(changeRequestRepository.findByStatus(ProfileChangeStatus.APPROVED,
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("reviewedAt"), Sort.Order.asc("id")))))
                .thenReturn(page);

        Page<ProfileChangeRequest> result = service.listByStatus(ProfileChangeStatus.APPROVED, pageable, "ASC");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listByStatus_defaultsToDescWhenDirectionInvalid() {
        Pageable pageable = PageRequest.of(0, 10);
        var request = pendingRequest("user-1");
        Page<ProfileChangeRequest> page = new PageImpl<>(java.util.List.of(request));
        when(changeRequestRepository.findByStatus(ProfileChangeStatus.PENDING,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))))
                .thenReturn(page);

        Page<ProfileChangeRequest> result = service.listByStatus(ProfileChangeStatus.PENDING, pageable, "INVALID");

        assertThat(result.getContent()).hasSize(1);
    }

    // ===== reject non-PENDING =====

    @Test
    void reject_nonPending_throwsBadRequest() {
        var request = pendingRequest("user-1");
        request.setStatus(ProfileChangeStatus.REJECTED);
        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(DomainBadRequestException.class,
                () -> service.reject(1L, "admin-1", "Nope", "req-1", "127.0.0.1", "TestAgent"));
    }

    // ===== reject not found =====

    @Test
    void reject_notFound_throwsNotFoundException() {
        when(changeRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.reject(99L, "admin-1", "Nope", "req-1", "127.0.0.1", "TestAgent"));
    }

    @Test
    void approve_malformedChangesJson_throwsIllegalStateException() {
        var request = new ProfileChangeRequest("user-1",
                "not-valid-json", "{}",
                ProfileChangeStatus.PENDING, "PASS", null);
        try {
            var idField = ProfileChangeRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(
                new UserAccount("user-1", "OldName", "u@example.com", null)));

        assertThrows(IllegalStateException.class,
                () -> service.approve(1L, "admin-1", "req-1", "127.0.0.1", "TestAgent"));
    }

    @Test
    void parseJson_malformedJson_throwsIllegalStateException() throws Exception {
        java.lang.reflect.Method method = ProfileReviewService.class.getDeclaredMethod("parseJson", String.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, "not-valid-json"));
    }

    @Test
    void toJson_withBadObject_throwsIllegalStateException() throws Exception {
        java.lang.reflect.Method method = ProfileReviewService.class.getDeclaredMethod("toJson", Object.class);
        method.setAccessible(true);

        Object badObject = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() { return this; }
        };

        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, badObject));
    }
}
