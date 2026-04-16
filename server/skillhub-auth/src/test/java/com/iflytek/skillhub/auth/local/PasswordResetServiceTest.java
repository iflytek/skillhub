package com.iflytek.skillhub.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;

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
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setCodeExpiry(Duration.ofMinutes(10));
        properties.setEmailFromAddress("noreply@skillhub.local");
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
    }

    @Test
    void requestPasswordReset_withEligibleEmail_savesRequestAndSendsEmail() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(
                Optional.of(new LocalCredential("usr_1", "alice", "encoded"))
        );
        given(resetRequestRepository.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(Instant.class))
        ).willReturn(List.of());
        given(passwordEncoder.encode(anyString())).willReturn("encoded-value");

        service.requestPasswordReset("alice@example.com");

        verify(resetRequestRepository).save(any(PasswordResetRequest.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestPasswordReset_withUnknownEmail_doesNothing() {
        given(userAccountRepository.findByEmailIgnoreCase("ghost@example.com")).willReturn(Optional.empty());

        service.requestPasswordReset("ghost@example.com");

        verify(resetRequestRepository, never()).save(any(PasswordResetRequest.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestPasswordReset_withInvalidEmail_throwsBadRequest() {
        assertThatThrownBy(() -> service.requestPasswordReset("alice"))
                .isInstanceOf(AuthFlowException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userAccountRepository, resetRequestRepository, mailSender);
    }

    @Test
    void requestPasswordReset_emailFailure_doesNotThrowForAnonymousFlow() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(credentialRepository.findByUserId("usr_1")).willReturn(
                Optional.of(new LocalCredential("usr_1", "alice", "encoded"))
        );
        given(resetRequestRepository.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(Instant.class))
        ).willReturn(List.of());
        given(passwordEncoder.encode(anyString())).willReturn("encoded-value");

        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        service.requestPasswordReset("alice@example.com");

        verify(resetRequestRepository).save(any(PasswordResetRequest.class));
    }

    @Test
    void adminTriggerPasswordReset_withUnknownUser_throwsNotFound() {
        given(userAccountRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminTriggerPasswordReset("missing", "admin_1"))
                .isInstanceOf(AuthFlowException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void confirmPasswordReset_withValidCode_updatesCredential() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "old-password");
        PasswordResetRequest request = new PasswordResetRequest(
                "usr_1",
                "alice@example.com",
                "encoded-code",
                Instant.now().plus(Duration.ofMinutes(5)),
                false,
                null
        );

        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(resetRequestRepository.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(Instant.class))
        ).willReturn(List.of(request));
        given(passwordEncoder.matches("123456", "encoded-code")).willReturn(true);
        given(credentialRepository.findByUserId("usr_1")).willReturn(Optional.of(credential));
        given(passwordEncoder.encode("Abcd123!")).willReturn("new-password-hash");

        service.confirmPasswordReset("alice@example.com", "123456", "Abcd123!");

        assertThat(credential.getPasswordHash()).isEqualTo("new-password-hash");
        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        verify(credentialRepository).save(credential);

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository, atLeastOnce()).save(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .anySatisfy(captured -> assertThat(captured.getConsumedAt()).isNotNull());
    }

    @Test
    void confirmPasswordReset_withInvalidCode_throwsBadRequest() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        LocalCredential credential = new LocalCredential("usr_1", "alice", "old-password");
        PasswordResetRequest request = new PasswordResetRequest(
                "usr_1",
                "alice@example.com",
                "encoded-code",
                Instant.now().plus(Duration.ofMinutes(5)),
                false,
                null
        );

        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.of(user));
        given(resetRequestRepository.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(Instant.class))
        ).willReturn(List.of(request));
        given(passwordEncoder.matches("654321", "encoded-code")).willReturn(false);

        assertThatThrownBy(() -> service.confirmPasswordReset("alice@example.com", "654321", "Abcd123!"))
                .isInstanceOf(AuthFlowException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmPasswordReset_withInvalidEmail_throwsBadRequest() {
        assertThatThrownBy(() -> service.confirmPasswordReset("alice", "123456", "Abcd123!"))
                .isInstanceOf(AuthFlowException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userAccountRepository, resetRequestRepository, credentialRepository);
    }

    @Test
    void adminTriggerPasswordReset_forDisabledUser_throwsBadRequest() {
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.adminTriggerPasswordReset("usr_1", "admin_1"))
                .isInstanceOf(AuthFlowException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
