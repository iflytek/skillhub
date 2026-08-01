
package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import java.lang.reflect.Method;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Behavior-level unit tests for {@link LdapAuthService} identity provisioning.
 *
 * <p>These tests exercise the {@code findOrCreateLdapUser} / {@code ensureUserCanLogin} logic via
 * reflection, with all repositories mocked, so they cover the security-critical behavior called out
 * in the PR review (email-collision takeover, duplicate provisioning on repeat login, attribute
 * synchronization, and disabled-account rejection) without requiring a live LDAP directory.
 */
class LdapAuthServiceTest {

    private static final String SUBJECT = "entry-uuid-123";
    private static final String EMAIL = "alice@example.com";
    private static final String DISPLAY_NAME = "Alice";

    private LdapProperties ldapProperties;
    private UserAccountRepository userAccountRepository;
    private UserRoleBindingRepository userRoleBindingRepository;
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;
    private IdentityBindingRepository identityBindingRepository;
    private EntityManager entityManager;
    private PlatformTransactionManager transactionManager;
    private LdapAuthService ldapAuthService;

    @BeforeEach
    void setUp() {
        ldapProperties = new LdapProperties();
        userAccountRepository = mock(UserAccountRepository.class);
        userRoleBindingRepository = mock(UserRoleBindingRepository.class);
        globalNamespaceMembershipService = mock(GlobalNamespaceMembershipService.class);
        identityBindingRepository = mock(IdentityBindingRepository.class);
        entityManager = mock(EntityManager.class);
        transactionManager = mock(PlatformTransactionManager.class);
        ldapAuthService = new LdapAuthService(
            ldapProperties,
            userAccountRepository,
            userRoleBindingRepository,
            globalNamespaceMembershipService,
            identityBindingRepository,
            entityManager,
            transactionManager);
    }

    /** Directory attributes: subject=entryUUID, email=mail, displayName=displayName. */
    private static Attributes directoryAttributes(String subject, String email, String displayName) {
        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("entryUUID", subject));
        attrs.put(new BasicAttribute("mail", email));
        attrs.put(new BasicAttribute("displayName", displayName));
        return attrs;
    }

    private UserAccount invokeFindOrCreate(String username, Attributes attrs) throws Exception {
        Method m = LdapAuthService.class.getDeclaredMethod("findOrCreateLdapUser", String.class, Attributes.class);
        m.setAccessible(true);
        return (UserAccount) m.invoke(ldapAuthService, username, attrs);
    }

    @Test
    void firstLogin_provisionsNewAccountAndBindsSubject() throws Exception {
        // Given — no existing binding and no email collision
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.empty());
        given(userAccountRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.empty());
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        UserAccount created = invokeFindOrCreate("alice", directoryAttributes(SUBJECT, EMAIL, DISPLAY_NAME));

        // Then — new active account bound to the LDAP subject; placeholder never used as identity key
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        verify(globalNamespaceMembershipService).ensureMember(created.getId());
        verify(identityBindingRepository).saveAndFlush(any(IdentityBinding.class));
    }

    @Test
    void repeatLogin_hitsExistingBindingBySubject_noDuplicateAccount() throws Exception {
        // Given — the LDAP subject is already bound to an account (prior login)
        String existingUserId = "usr_existing";
        UserAccount existing = new UserAccount(existingUserId, "Old Name", EMAIL, null);
        existing.setStatus(UserStatus.ACTIVE);
        IdentityBinding binding = new IdentityBinding(existingUserId, "ldap", SUBJECT, "alice");
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.of(binding));
        given(userAccountRepository.findById(existingUserId)).willReturn(Optional.of(existing));
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        // When — same subject logs in again
        UserAccount result = invokeFindOrCreate("alice", directoryAttributes(SUBJECT, EMAIL, DISPLAY_NAME));

        // Then — returns the same account, never provisions a new one
        assertThat(result.getId()).isEqualTo(existingUserId);
        verify(userAccountRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
            u -> !existingUserId.equals(u.getId())));
        // Critical: no new binding written on repeat login
        verify(identityBindingRepository, never()).saveAndFlush(any(IdentityBinding.class));
    }

    @Test
    void repeatLogin_refreshesAttributesFromDirectory() throws Exception {
        // Given — a returning user whose display name and email changed in the directory
        String userId = "usr_alice";
        UserAccount existing = new UserAccount(userId, "Old Name", "old@example.com", null);
        existing.setStatus(UserStatus.ACTIVE);
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.of(new IdentityBinding(userId, "ldap", SUBJECT, "alice")));
        given(userAccountRepository.findById(userId)).willReturn(Optional.of(existing));
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        // When — directory now reports a new display name and email
        UserAccount result = invokeFindOrCreate("alice",
            directoryAttributes(SUBJECT, "new@example.com", "New Name"));

        // Then — attributes are refreshed on this login (not only at first creation)
        assertThat(result.getDisplayName()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void emailCollision_refusesSilentInheritance_throwsConflict() throws Exception {
        // Given — a different identity provider already owns this email
        String otherUserId = "usr_oauth";
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.empty()); // no LDAP binding yet
        given(userAccountRepository.findByEmailIgnoreCase(EMAIL))
            .willReturn(Optional.of(new UserAccount(otherUserId, "OAuth User", EMAIL, null)));

        // When — must NOT silently inherit the OAuth account / its roles.
        // Reflection wraps checked exceptions in InvocationTargetException, so unwrap and assert
        // the inner AuthFlowException carries a 409 CONFLICT with the emailConflict message key.
        AuthFlowException thrown = null;
        try {
            invokeFindOrCreate("alice", directoryAttributes(SUBJECT, EMAIL, DISPLAY_NAME));
        } catch (java.lang.reflect.InvocationTargetException ite) {
            thrown = (AuthFlowException) ite.getCause();
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(thrown.getMessageCode()).isEqualTo("error.auth.ldap.emailConflict");

        // No account created, no binding written
        verify(userAccountRepository, never()).save(any(UserAccount.class));
        verify(identityBindingRepository, never()).saveAndFlush(any(IdentityBinding.class));
    }

    @Test
    void noEmail_usesPlaceholderAccount_doesNotCollideAcrossLogins() throws Exception {
        // Given — directory entry has no mail attribute; subject is the only stable key
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.empty());
        // No email -> no email-collision lookup happens; placeholder email is generated
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("entryUUID", SUBJECT));
        attrs.put(new BasicAttribute("displayName", DISPLAY_NAME));

        // When
        UserAccount created = invokeFindOrCreate("bob", attrs);

        // Then — placeholder email follows the ldap:{username}@internal convention
        assertThat(created.getEmail()).isEqualTo("ldap:bob@internal");
        verify(userAccountRepository, never()).findByEmailIgnoreCase(any());
        verify(identityBindingRepository).saveAndFlush(any(IdentityBinding.class));
    }

    @Test
    void missingSubjectAttribute_throwsServiceUnavailable() {
        // Given — bind succeeded but the entry lacks the configured subject attribute
        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("mail", EMAIL));

        // When & Then — a 503 (not a 401) so the user is not misled into thinking the password is wrong
        assertThatThrownBy(() -> invokeFindOrCreate("alice", attrs))
            .hasCauseInstanceOf(AuthFlowException.class);
        try {
            invokeFindOrCreate("alice", attrs);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            AuthFlowException cause = (AuthFlowException) ite.getCause();
            assertThat(cause.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Test
    void ensureUserCanLogin_rejectsDisabledAccount() throws Exception {
        // The disabled-account path must surface a FORBIDDEN (propagated, not masked as 401)
        UserAccount disabled = new UserAccount("usr_x", "X", "x@example.com", null);
        disabled.setStatus(UserStatus.DISABLED);

        Method m = LdapAuthService.class.getDeclaredMethod("ensureUserCanLogin", UserAccount.class);
        m.setAccessible(true);
        try {
            m.invoke(ldapAuthService, disabled);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            AuthFlowException cause = (AuthFlowException) ite.getCause();
            assertThat(cause.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(cause.getMessageCode()).isEqualTo("error.auth.local.accountDisabled");
        }
    }

    @Test
    void displayNameFallsBackToCn_whenDisplayNameAttributeAbsent() throws Exception {
        // Given — directory has no displayName but has cn; configured fallback defaults to "cn"
        assertThat(ldapProperties.getDisplayNameFallbackAttribute()).isEqualTo("cn");
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.empty());
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("entryUUID", SUBJECT));
        attrs.put(new BasicAttribute("cn", "Common Name"));

        // When
        UserAccount created = invokeFindOrCreate("carol", attrs);

        // Then — display name falls back to the configured cn attribute
        assertThat(created.getDisplayName()).isEqualTo("Common Name");
    }

    @Test
    void returningUser_emailCollision_refusesSilentUpdate_throwsConflict() throws Exception {
        // Given — the LDAP subject is bound, but the directory now reports an email that already
        // belongs to a different account. The refresh must refuse to adopt it (409), matching the
        // first-login email-collision rule.
        String userId = "usr_alice";
        UserAccount existing = new UserAccount(userId, "Alice", "alice@example.com", null);
        existing.setStatus(UserStatus.ACTIVE);
        UserAccount other = new UserAccount("usr_other", "Other User", "other@example.com", null);
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.of(new IdentityBinding(userId, "ldap", SUBJECT, "alice")));
        given(userAccountRepository.findById(userId)).willReturn(Optional.of(existing));
        given(userAccountRepository.findByEmailIgnoreCase("other@example.com")).willReturn(Optional.of(other));

        AuthFlowException thrown = null;
        try {
            invokeFindOrCreate("alice", directoryAttributes(SUBJECT, "other@example.com", "Alice"));
        } catch (java.lang.reflect.InvocationTargetException ite) {
            thrown = (AuthFlowException) ite.getCause();
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(thrown.getMessageCode()).isEqualTo("error.auth.ldap.emailConflict");
        // The user's own email must remain untouched.
        assertThat(existing.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void returningUser_sameEmail_isAllowedToRefresh() throws Exception {
        // Given — the directory reports the same email the bound account already owns; the
        // collision lookup must exclude the user's own account.
        String userId = "usr_alice";
        UserAccount existing = new UserAccount(userId, "Alice", "alice@example.com", null);
        existing.setStatus(UserStatus.ACTIVE);
        given(identityBindingRepository.findByProviderCodeAndSubject("ldap", SUBJECT))
            .willReturn(Optional.of(new IdentityBinding(userId, "ldap", SUBJECT, "alice")));
        given(userAccountRepository.findById(userId)).willReturn(Optional.of(existing));
        given(userAccountRepository.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        UserAccount result = invokeFindOrCreate("alice",
            directoryAttributes(SUBJECT, "alice@example.com", "Alice Smith"));

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void buildJndiEnvironment_includesCustomTrustStoreSettings() {
        ldapProperties.setUrl("ldaps://ldap.example.com:636");
        ldapProperties.setTlsTrustStorePath("/certs/ldap-truststore.jks");
        ldapProperties.setTlsTrustStorePassword("secret");
        ldapProperties.setTlsTrustStoreType("PKCS12");

        java.util.Hashtable<String, String> env = ldapAuthService.buildJndiEnvironment(null, null);

        assertThat(env)
            .containsEntry("javax.net.ssl.trustStore", "/certs/ldap-truststore.jks")
            .containsEntry("javax.net.ssl.trustStorePassword", "secret")
            .containsEntry("javax.net.ssl.trustStoreType", "PKCS12");
    }

    @Test
    void isTlsFailure_detectsSslHandshakeInCauseChain() {
        javax.naming.CommunicationException comm = new javax.naming.CommunicationException("LDAP connect failed");
        comm.initCause(new javax.net.ssl.SSLHandshakeException("PKIX path building failed"));

        assertThat(LdapAuthService.isTlsFailure(comm)).isTrue();
    }

    @Test
    void isTlsFailure_detectsDeepCertificateException() {
        javax.naming.CommunicationException comm = new javax.naming.CommunicationException("LDAP connect failed");
        comm.initCause(new java.io.IOException("TLS handshake failed",
            new java.security.cert.CertificateException("not trusted")));

        assertThat(LdapAuthService.isTlsFailure(comm)).isTrue();
    }

    @Test
    void isTlsFailure_ignoresPlainConnectionFailures() {
        javax.naming.CommunicationException comm = new javax.naming.CommunicationException("LDAP connect failed");
        comm.initCause(new java.io.IOException("Connection refused"));

        assertThat(LdapAuthService.isTlsFailure(comm)).isFalse();
    }
}
