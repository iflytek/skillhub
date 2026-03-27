package com.iflytek.skillhub.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import com.iflytek.skillhub.domain.auth.PasswordResetRequestRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetRequestRepository resetRequestRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private LocalCredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    private PasswordResetService service;

    @BeforeEach
    void setUp() throws Exception {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setTokenExpiry(Duration.ofMinutes(30));
        properties.setEmailFromAddress("noreply@skillhub.test");
        properties.setEmailFromName("SkillHub");

        service = new PasswordResetService(
            resetRequestRepository,
            userAccountRepository,
            credentialRepository,
            new PasswordPolicyValidator(),
            passwordEncoder,
            mailSender,
            properties
        );

        var field = PasswordResetService.class.getDeclaredField("publicBaseUrl");
        field.setAccessible(true);
        field.set(service, "http://localhost:3000");
    }

    @Test
    void requestPasswordReset_withValidEmail_sendsEmail() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "hash");

        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode(any(String.class))).willReturn("token-hash");

        service.requestPasswordReset("alice@example.com");

        verify(resetRequestRepository).invalidatePendingRequests("usr_1");
        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("usr_1");
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestPasswordReset_withValidUsername_sendsEmail() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "hash");

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode(any(String.class))).willReturn("token-hash");

        service.requestPasswordReset("alice");

        verify(resetRequestRepository).invalidatePendingRequests("usr_1");
        verify(resetRequestRepository).save(any(PasswordResetRequest.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestPasswordReset_withIneligibleUser_doesNotSendEmail() {
        UserAccount user = new UserAccount("usr_1", "alice", null, null);
        user.setStatus(UserStatus.ACTIVE);

        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));

        service.requestPasswordReset("alice@example.com");

        verify(resetRequestRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestPasswordReset_invalidatesExistingTokens() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "hash");

        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode(any(String.class))).willReturn("token-hash");

        service.requestPasswordReset("alice@example.com");

        verify(resetRequestRepository).invalidatePendingRequests("usr_1");
    }

    @Test
    void adminTriggerPasswordReset_withEligibleUser_sendsEmail() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "hash");

        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode(any(String.class))).willReturn("token-hash");

        service.adminTriggerPasswordReset("usr_1", "admin_1");

        verify(resetRequestRepository).invalidatePendingRequests("usr_1");
        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository).save(captor.capture());
        assertThat(captor.getValue().isRequestedByAdmin()).isTrue();
        assertThat(captor.getValue().getRequestedByUserId()).isEqualTo("admin_1");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void adminTriggerPasswordReset_withIneligibleUser_throwsException() {
        UserAccount user = new UserAccount("usr_1", "alice", null, null);
        user.setStatus(UserStatus.ACTIVE);

        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.adminTriggerPasswordReset("usr_1", "admin_1"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.password.reset.not.eligible");
    }

    @Test
    void confirmPasswordReset_withValidToken_updatesPassword() {
        PasswordResetRequest request = new PasswordResetRequest(
            "usr_1", "alice@example.com", "token-hash", Instant.now().plusSeconds(1800), false, null
        );
        LocalCredential credential = new LocalCredential("usr_1", "alice", "old-hash");
        credential.setFailedAttempts(3);
        credential.setLockedUntil(Instant.now().plusSeconds(600));

        given(resetRequestRepository.findAllPendingNotExpired(any(Instant.class))).willReturn(List.of(request));
        given(passwordEncoder.matches("valid-token", "token-hash")).willReturn(true);
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode("NewPass123!")).willReturn("new-hash");

        service.confirmPasswordReset("valid-token", "NewPass123!");

        assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        verify(credentialRepository).save(credential);
        verify(resetRequestRepository).save(request);
        verify(resetRequestRepository).invalidatePendingRequests("usr_1");
    }

    @Test
    void confirmPasswordReset_withInvalidToken_throwsException() {
        given(resetRequestRepository.findAllPendingNotExpired(any(Instant.class))).willReturn(List.of());

        assertThatThrownBy(() -> service.confirmPasswordReset("invalid-token", "NewPass123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.password.reset.invalid.token");
    }

    @Test
    void confirmPasswordReset_withExpiredToken_throwsException() {
        PasswordResetRequest request = new PasswordResetRequest(
            "usr_1", "alice@example.com", "token-hash", Instant.now().minusSeconds(1), false, null
        );

        given(resetRequestRepository.findAllPendingNotExpired(any(Instant.class))).willReturn(List.of());

        assertThatThrownBy(() -> service.confirmPasswordReset("expired-token", "NewPass123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.password.reset.invalid.token");
    }

    @Test
    void confirmPasswordReset_withConsumedToken_throwsException() {
        PasswordResetRequest request = new PasswordResetRequest(
            "usr_1", "alice@example.com", "token-hash", Instant.now().plusSeconds(1800), false, null
        );
        request.markConsumed();

        given(resetRequestRepository.findAllPendingNotExpired(any(Instant.class))).willReturn(List.of());

        assertThatThrownBy(() -> service.confirmPasswordReset("consumed-token", "NewPass123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.password.reset.invalid.token");
    }

}
