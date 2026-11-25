package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse.LoginOption;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse.OrganizationLoginOptions;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseLoginDiscoveryService {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){1,63}");
    private final OrganizationRepository organizations;
    private final LoginConnectionRepository connections;
    private final AuthMethodCatalog publicMethods;
    private final EnterpriseIdentityRolloutProperties rollout;

    public EnterpriseLoginDiscoveryService(
            OrganizationRepository organizations,
            LoginConnectionRepository connections,
            AuthMethodCatalog publicMethods,
            EnterpriseIdentityRolloutProperties rollout
    ) {
        this.organizations = organizations;
        this.connections = connections;
        this.publicMethods = publicMethods;
        this.rollout = rollout;
    }

    public EnterpriseLoginDiscoveryResponse discover(String rawIdentifier, String returnTo) {
        List<AuthMethodResponse> fallback = publicMethods.listMethods(returnTo).stream()
                .filter(method -> !"ENTERPRISE_REDIRECT".equals(method.methodType()))
                .toList();
        if (!rollout.isOidcLoginEnabled()) {
            return new EnterpriseLoginDiscoveryResponse(fallback, List.of());
        }
        Optional<Organization> organization = resolve(rawIdentifier)
                .filter(item -> item.getStatus() == OrganizationStatus.ACTIVE)
                .filter(item -> rollout.getOrganizationAllowlist().contains(item.getId()));
        if (organization.isEmpty()) {
            return new EnterpriseLoginDiscoveryResponse(fallback, List.of());
        }
        Organization item = organization.orElseThrow();
        List<LoginOption> options = connections
                .findByOrganizationIdAndStatusOrderByDisplayNameAsc(item.getId(), LoginConnectionStatus.ACTIVE)
                .stream()
                .filter(connection -> connection.getActiveRevisionId() != null)
                .map(connection -> new LoginOption(
                        connection.getDisplayName(),
                        startUrl(connection.getPublicHandle(), returnTo)
                ))
                .toList();
        if (options.isEmpty()) {
            return new EnterpriseLoginDiscoveryResponse(fallback, List.of());
        }
        return new EnterpriseLoginDiscoveryResponse(
                fallback,
                List.of(new OrganizationLoginOptions(item.getSlug(), item.getDisplayName(), options))
        );
    }

    private Optional<Organization> resolve(String rawIdentifier) {
        if (rawIdentifier == null) return Optional.empty();
        String identifier = rawIdentifier.trim().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(identifier).matches()) return Optional.empty();
        return organizations.findBySlug(identifier);
    }

    private static String startUrl(String handle, String returnTo) {
        String url = "/api/v1/auth/enterprise/" + handle + "/start";
        String safeReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        if (safeReturnTo != null) {
            url += "?returnTo=" + URLEncoder.encode(safeReturnTo, StandardCharsets.UTF_8);
        }
        return url;
    }
}
