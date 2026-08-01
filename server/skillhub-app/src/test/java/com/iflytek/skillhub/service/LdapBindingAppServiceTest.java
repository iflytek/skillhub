package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.ldap.LdapAuthService;
import com.iflytek.skillhub.auth.ldap.LdapAuthService.LdapIdentity;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

class LdapBindingAppServiceTest {

    private static final String CURRENT_USER = "usr_current";

    private LdapAuthService ldapAuthService;
    private IdentityBindingRepository identityBindingRepository;
    private UserAccountRepository userAccountRepository;
    private LdapBindingAppService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ldapAuthService = mock(LdapAuthService.class);
        ObjectProvider<LdapAuthService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ldapAuthService);
        identityBindingRepository = mock(IdentityBindingRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        service = new LdapBindingAppService(provider, identityBindingRepository, userAccountRepository);
    }

    @Test
    void bind_createsBindingForCurrentAccount() {
        when(ldapAuthService.resolveIdentity("alice", "secret"))
            .thenReturn(new LdapIdentity("alice", "entry-uuid-1", "alice@example.com", "Alice"));
        when(identityBindingRepository.findByProviderCodeAndSubject("ldap", "entry-uuid-1"))
            .thenReturn(Optional.empty());
        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com"))
            .thenReturn(Optional.empty());

        service.bindLdapIdentity(CURRENT_USER, "alice", "secret");

        verify(identityBindingRepository).save(any(IdentityBinding.class));
    }

    @Test
    void bind_whenSubjectBelongsToAnotherAccount_throwsConflict() {
        when(ldapAuthService.resolveIdentity("alice", "secret"))
            .thenReturn(new LdapIdentity("alice", "entry-uuid-1", null, "Alice"));
        IdentityBinding other = new IdentityBinding("usr_other", "ldap", "entry-uuid-1", "alice");
        when(identityBindingRepository.findByProviderCodeAndSubject("ldap", "entry-uuid-1"))
            .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.bindLdapIdentity(CURRENT_USER, "alice", "secret"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.bindingTaken");
            });
        verify(identityBindingRepository, never()).save(any());
    }

    @Test
    void bind_whenSubjectAlreadyBoundToCurrentAccount_isIdempotent() {
        when(ldapAuthService.resolveIdentity("alice", "secret"))
            .thenReturn(new LdapIdentity("alice", "entry-uuid-1", "alice@example.com", "Alice"));
        IdentityBinding own = new IdentityBinding(CURRENT_USER, "ldap", "entry-uuid-1", "alice");
        when(identityBindingRepository.findByProviderCodeAndSubject("ldap", "entry-uuid-1"))
            .thenReturn(Optional.of(own));
        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com"))
            .thenReturn(Optional.of(new UserAccount(CURRENT_USER, "Alice", "alice@example.com", null)));

        service.bindLdapIdentity(CURRENT_USER, "alice", "secret");

        verify(identityBindingRepository, never()).save(any());
    }

    @Test
    void bind_whenEmailBelongsToAnotherAccount_throwsConflict() {
        when(ldapAuthService.resolveIdentity("alice", "secret"))
            .thenReturn(new LdapIdentity("alice", "entry-uuid-1", "alice@example.com", "Alice"));
        when(identityBindingRepository.findByProviderCodeAndSubject("ldap", "entry-uuid-1"))
            .thenReturn(Optional.empty());
        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com"))
            .thenReturn(Optional.of(new UserAccount("usr_other", "Other", "alice@example.com", null)));

        assertThatThrownBy(() -> service.bindLdapIdentity(CURRENT_USER, "alice", "secret"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.emailConflict");
            });
        verify(identityBindingRepository, never()).save(any());
    }

    @Test
    void bind_whenLdapDisabled_throwsServiceUnavailable() {
        ObjectProvider<LdapAuthService> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        LdapBindingAppService disabledService =
            new LdapBindingAppService(emptyProvider, identityBindingRepository, userAccountRepository);

        assertThatThrownBy(() -> disabledService.bindLdapIdentity(CURRENT_USER, "alice", "secret"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.disabled");
            });
    }
}
