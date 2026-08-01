package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.ldap.LdapAuthService;
import com.iflytek.skillhub.auth.ldap.LdapAuthService.LdapIdentity;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit LDAP identity-binding flow: a signed-in user proves ownership of a directory identity
 * with their LDAP credentials and attaches it to their current account. This is the
 * self-service counterpart of the first-login email-conflict refusal — instead of silently
 * inheriting an existing account, the user consciously binds the LDAP identity to it.
 */
@Service
public class LdapBindingAppService {

    private static final String LDAP_PROVIDER = "ldap";

    private final ObjectProvider<LdapAuthService> ldapAuthServiceProvider;
    private final IdentityBindingRepository identityBindingRepository;
    private final UserAccountRepository userAccountRepository;

    public LdapBindingAppService(ObjectProvider<LdapAuthService> ldapAuthServiceProvider,
                                 IdentityBindingRepository identityBindingRepository,
                                 UserAccountRepository userAccountRepository) {
        this.ldapAuthServiceProvider = ldapAuthServiceProvider;
        this.identityBindingRepository = identityBindingRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public void bindLdapIdentity(String currentUserId, String username, String password) {
        LdapAuthService ldapAuthService = ldapAuthServiceProvider.getIfAvailable();
        if (ldapAuthService == null) {
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.disabled");
        }
        LdapIdentity identity = ldapAuthService.resolveIdentity(username, password);

        var existingBinding = identityBindingRepository
            .findByProviderCodeAndSubject(LDAP_PROVIDER, identity.subject());
        if (existingBinding.isPresent()) {
            if (!existingBinding.get().getUserId().equals(currentUserId)) {
                throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.ldap.bindingTaken");
            }
            // Already bound to the current account — idempotent success.
            return;
        }

        String email = identity.email();
        if (email != null && !email.isEmpty()) {
            userAccountRepository.findByEmailIgnoreCase(email.toLowerCase(Locale.ROOT))
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.ldap.emailConflict");
                });
        }

        try {
            identityBindingRepository.save(
                new IdentityBinding(currentUserId, LDAP_PROVIDER, identity.subject(), identity.username()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent bind for the same subject won the (provider_code, subject) race.
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.ldap.bindingTaken");
        }
    }
}
