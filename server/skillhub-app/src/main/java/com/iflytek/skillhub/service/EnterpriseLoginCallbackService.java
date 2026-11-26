package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionConfiguration;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginProtocolClient;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretReader;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociation;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociationService;
import com.iflytek.skillhub.auth.federation.core.Assurance;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationPolicySettings;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.federation.core.SubjectType;
import com.iflytek.skillhub.auth.federation.core.VerifiedEmail;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.session.IdentitySessionOriginService;
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

/** Completes an enterprise OIDC callback and hands only verified facts to the identity core. */
@Service
public class EnterpriseLoginCallbackService {
    private final EnterpriseLoginStartService transactions;
    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final EnterpriseIdentityRolloutProperties rollout;
    private final LoginConnectionSecretReader secrets;
    private final OidcLoginProtocolClient oidc;
    private final ObjectProvider<EnterpriseIdentityAssociationService> associations;
    private final UserAccountRepository accounts;
    private final UserRoleBindingRepository roles;
    private final PlatformSessionService sessions;
    private final IdentitySessionOriginService sessionOrigins;

    public EnterpriseLoginCallbackService(
            EnterpriseLoginStartService transactions,
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            OrganizationRepository organizations,
            OrganizationMembershipRepository memberships,
            EnterpriseIdentityRolloutProperties rollout,
            LoginConnectionSecretReader secrets,
            OidcLoginProtocolClient oidc,
            ObjectProvider<EnterpriseIdentityAssociationService> associations,
            UserAccountRepository accounts,
            UserRoleBindingRepository roles,
            PlatformSessionService sessions,
            IdentitySessionOriginService sessionOrigins
    ) {
        this.transactions = transactions;
        this.connections = connections;
        this.revisions = revisions;
        this.organizations = organizations;
        this.memberships = memberships;
        this.rollout = rollout;
        this.secrets = secrets;
        this.oidc = oidc;
        this.associations = associations;
        this.accounts = accounts;
        this.roles = roles;
        this.sessions = sessions;
        this.sessionOrigins = sessionOrigins;
    }

    public CompleteResult complete(String publicHandle, String state, String code, String binding,
                                   HttpServletRequest request) {
        EnterpriseLoginStartService.EnterpriseLoginTransaction transaction = transactions.consume(state, binding);
        if (!publicHandle.equals(transaction.publicHandle()) || !state.equals(transaction.state())
                || code == null || code.isBlank()
                || !transaction.redirectUri().equals(rollout.callbackUri(publicHandle).toString())) {
            throw unavailable();
        }
        LoginConnection connection = connections.findByPublicHandle(publicHandle)
                .filter(item -> item.getId().equals(transaction.connectionId()))
                .filter(item -> item.getOrganizationId().equals(transaction.organizationId()))
                .filter(item -> item.getStatus() == LoginConnectionStatus.ACTIVE)
                .filter(item -> transaction.revisionId().equals(item.getActiveRevisionId()))
                .orElseThrow(this::unavailable);
        if (organizations.findById(transaction.organizationId())
                .filter(item -> item.getStatus() == OrganizationStatus.ACTIVE)
                .filter(item -> rollout.getOrganizationAllowlist().contains(item.getId())).isEmpty()) {
            throw unavailable();
        }
        LoginConnectionRevision revision = revisions.findById(transaction.revisionId()).orElseThrow(this::unavailable);
        OidcConnectionConfiguration config = revision.getConfiguration();
        long version = revision.getSecretBindingVersion() == null ? 0 : revision.getSecretBindingVersion();
        String secret = secrets.read(transaction.organizationId(), connection.getId(), version)
                .orElseThrow(this::unavailable);
        OidcLoginProtocolClient.ProviderMetadata metadata = oidc.metadata(config);
        OidcLoginProtocolClient.TokenResponse token = oidc.exchangeCode(
                metadata, config.clientId(), secret, code, transaction.redirectUri(), transaction.codeVerifier());
        JWTClaimsSet idToken = oidc.verifyIdToken(token.idToken(), metadata, config.issuer(),
                config.clientId(), transaction.nonce());
        JsonNode userInfo = oidc.userInfo(metadata, token.accessToken());
        verifyUserInfoSubject(userInfo, idToken.getSubject());
        IdentityAssertion assertion = assertion(transaction, config, idToken, userInfo);
        EnterpriseIdentityAssociation association = associations.getIfAvailable(() -> {
            throw unavailable();
        }).associate(
                assertion, IdentityCorrelationPolicySettings.disabled());
        UserAccount account = accounts.findById(association.userId()).filter(UserAccount::isActive)
                .orElseThrow(this::unavailable);
        var organization = organizations.findById(transaction.organizationId())
                .filter(item -> item.getStatus() == OrganizationStatus.ACTIVE)
                .orElseThrow(this::unavailable);
        var membership = memberships.findByOrganizationIdAndId(transaction.organizationId(), association.membershipId())
                .filter(item -> item.getUserId().equals(account.getId()))
                .filter(item -> item.getStatus() == OrganizationMembershipStatus.ACTIVE)
                .orElseThrow(this::unavailable);
        Set<String> platformRoles = new HashSet<>();
        roles.findByUserId(account.getId()).forEach(bindingRole -> platformRoles.add(bindingRole.getRole().getCode()));
        PlatformPrincipal principal = new PlatformPrincipal(account.getId(), account.getDisplayName(), account.getEmail(),
                account.getAvatarUrl(), "enterprise-oidc:" + connection.getId(),
                PlatformRoleDefaults.withDefaultUserRole(platformRoles));
        sessions.establishSession(principal, request, true);
        try {
            sessionOrigins.record(request.getSession().getId(), account.getId(), organization, membership,
                    connection.getId(), association);
        } catch (RuntimeException persistenceFailure) {
            request.getSession().invalidate();
            throw persistenceFailure;
        }
        request.getSession().setAttribute("enterprise.organizationId", transaction.organizationId());
        request.getSession().setAttribute("enterprise.connectionId", connection.getId());
        request.getSession().setAttribute("enterprise.membershipId", association.membershipId());
        return new CompleteResult(association, transaction.returnTo());
    }

    private static IdentityAssertion assertion(
            EnterpriseLoginStartService.EnterpriseLoginTransaction transaction,
            OidcConnectionConfiguration config,
            JWTClaimsSet idToken,
            JsonNode userInfo
    ) {
        String subject = idToken.getSubject();
        String email = firstText(idToken, "email");
        boolean emailVerified = Boolean.TRUE.equals(idToken.getClaim("email_verified"))
                || userInfo.path("email_verified").asBoolean(false);
        if (!emailVerified) {
            email = null;
        }
        String displayName = firstNonBlank(
                firstText(idToken, "name"), firstText(idToken, "preferred_username"),
                userInfo.path("name").asText(null), email, subject);
        Set<Assurance> assurance = new HashSet<>();
        assurance.add(new Assurance("oidc.id_token"));
        if (userInfo.size() > 0) {
            assurance.add(new Assurance("oidc.userinfo"));
        }
        return new IdentityAssertion(
                Optional.of(transaction.organizationId()), transaction.connectionId(), URI.create(config.issuer()),
                new SubjectRef(new SubjectType("oidc"), subject),
                email == null ? Optional.empty() : Optional.of(new VerifiedEmail(email)),
                Optional.ofNullable(firstNonBlank(firstText(idToken, "preferred_username"), subject)),
                Optional.of(displayName), Optional.ofNullable(userInfo.path("picture").asText(null)),
                assurance, Instant.now(), Map.of());
    }

    private static String firstText(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static void verifyUserInfoSubject(JsonNode userInfo, String idSubject) {
        if (userInfo.path("sub").isTextual() && !idSubject.equals(userInfo.path("sub").asText())) {
            throw new IllegalStateException("enterprise login unavailable");
        }
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("enterprise login unavailable");
    }

    public record CompleteResult(EnterpriseIdentityAssociation association, String returnTo) {}
}
