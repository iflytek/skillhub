package com.iflytek.skillhub.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-18T06:00:00Z"), ZoneOffset.UTC);

    @Mock
    private LocalCredentialRepository credentialRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserRoleBindingRepository userRoleBindingRepository;

    @Mock
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private LocalAuthService service;

    @BeforeEach
    void setUp() {
        service = new LocalAuthService(
            credentialRepository,
            userAccountRepository,
            userRoleBindingRepository,
            globalNamespaceMembershipService,
            new PasswordPolicyValidator(),
            passwordEncoder,
            CLOCK
        );
    }

    @Test
    void register_createsUserAndCredential() {
        given(credentialRepository.existsByUsernameIgnoreCase("alice")).willReturn(false);
        given(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("Abcd123!")).willReturn("encoded");
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());

        var principal = service.register("Alice", "Abcd123!", "alice@example.com");

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("alice");
        assertThat(principal.displayName()).isEqualTo("alice");
        assertThat(principal.email()).isEqualTo("alice@example.com");
        assertThat(principal.platformRoles()).containsExactly("USER");
        verify(credentialRepository).save(any(LocalCredential.class));
        verify(globalNamespaceMembershipService).ensureMember(userCaptor.getValue().getId());
    }

    @Test
    void login_withValidPassword_resetsCounters() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        credential.setFailedAttempts(3);
        credential.setLockedUntil(Instant.now(CLOCK).minusSeconds(60));
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        Role role = mock(Role.class);
        given(role.getCode()).willReturn("USER_ADMIN");
        UserRoleBinding binding = new UserRoleBinding("usr_1", role);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Abcd123!", "encoded")).willReturn(true);
        given(userRoleBindingRepository.findByUserId("usr_1")).willReturn(List.of(binding));

        var principal = service.login("alice", "Abcd123!");

        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        assertThat(principal.platformRoles()).containsExactly("USER_ADMIN");
    }

    @Test
    void login_withInvalidPassword_incrementsCounter() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("bad", "encoded")).willReturn(false);

        assertThatThrownBy(() -> service.login("alice", "bad"))
            .isInstanceOf(AuthFlowException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(credential.getFailedAttempts()).isEqualTo(1);
        verify(credentialRepository).save(credential);
    }

    @Test
    void login_afterMaxFailures_setsLockUsingInjectedClock() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        credential.setFailedAttempts(4);
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("bad", "encoded")).willReturn(false);

        assertThatThrownBy(() -> service.login("alice", "bad"))
            .isInstanceOf(AuthFlowException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(credential.getLockedUntil()).isEqualTo(Instant.now(CLOCK).plusSeconds(15 * 60));
    }

    @Test
    void login_whileLocked_reportsRemainingMinutesFromInjectedClock() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        credential.setLockedUntil(Instant.now(CLOCK).plusSeconds(5 * 60));
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "Abcd123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.local.locked");
    }

    @Test
    void login_withUnknownUsername_stillPerformsDummyPasswordCheck() {
        given(credentialRepository.findByUsernameIgnoreCase("ghost")).willReturn(Optional.empty());
        given(passwordEncoder.matches(eq("bad"), eq("$2a$12$8Q/2o2A0V.b18G2DutV4c.s5zZxH6MECM7tP8mYv6b6Q6x6o9v3vu")))
            .willReturn(false);

        assertThatThrownBy(() -> service.login("ghost", "bad"))
            .isInstanceOf(AuthFlowException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(passwordEncoder).matches("bad", "$2a$12$8Q/2o2A0V.b18G2DutV4c.s5zZxH6MECM7tP8mYv6b6Q6x6o9v3vu");
        verify(userAccountRepository, never()).findById(any());
    }

    @Test
    void login_withDisabledAccount_fails() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "Abcd123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.local.accountDisabled");
    }

    @Test
    void login_withPendingAccount_fails() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.PENDING);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "Abcd123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.local.accountPending");
    }

    @Test
    void login_withMergedAccount_fails() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.MERGED);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "Abcd123!"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.local.accountMerged");
    }

    @Test
    void login_withoutExplicitRoles_defaultsToUser() {
        LocalCredential credential = new LocalCredential("usr_1", "alice", "encoded");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);

        given(credentialRepository.findByUsernameIgnoreCase("alice")).willReturn(Optional.of(credential));
        given(userAccountRepository.findById("usr_1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Abcd123!", "encoded")).willReturn(true);
        given(userRoleBindingRepository.findByUserId("usr_1")).willReturn(List.of());

        var principal = service.login("alice", "Abcd123!");

        assertThat(principal.platformRoles()).containsExactly("USER");
    }

    @Test
    void register_rejectsInvalidEmailFormat() {
        given(credentialRepository.existsByUsernameIgnoreCase("alice")).willReturn(false);

        assertThatThrownBy(() -> service.register("Alice", "Abcd123!", "not-an-email"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("validation.auth.local.email.invalid");
    }

    @Test
    void register_rejectsBlankEmail() {
        given(credentialRepository.existsByUsernameIgnoreCase("alice")).willReturn(false);

        assertThatThrownBy(() -> service.register("Alice", "Abcd123!", "   "))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("validation.auth.local.email.notBlank");
    }
}
