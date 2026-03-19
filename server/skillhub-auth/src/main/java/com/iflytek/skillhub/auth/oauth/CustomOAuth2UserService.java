package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring Security OAuth user-service bridge that extracts provider claims,
 * evaluates access policy, and maps the result to a {@link PlatformPrincipal}.
 */
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Map<String, OAuthClaimsExtractor> extractors;
    private final AccessPolicy accessPolicy;
    private final IdentityBindingService identityBindingService;

    public CustomOAuth2UserService(List<OAuthClaimsExtractor> extractorList,
                                    AccessPolicy accessPolicy,
                                    IdentityBindingService identityBindingService) {
        this.extractors = extractorList.stream()
            .collect(Collectors.toMap(OAuthClaimsExtractor::getProvider, Function.identity()));
        this.accessPolicy = accessPolicy;
        this.identityBindingService = identityBindingService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthClaimsExtractor extractor = extractors.get(registrationId);
        if (extractor == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", "Unsupported: " + registrationId, null));
        }

        OAuthClaims claims = extractor.extract(request, oAuth2User);
        AccessDecision decision = accessPolicy.evaluate(claims);

        if (decision == AccessDecision.PENDING_APPROVAL) {
            identityBindingService.createPendingUserIfAbsent(claims);
            throw new AccountPendingException();
        }
        if (decision == AccessDecision.DENY) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "Access denied by policy", null));
        }

        PlatformPrincipal principal = identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);

        var attrs = new HashMap<>(oAuth2User.getAttributes());
        attrs.put("platformPrincipal", principal);

        var authorities = new LinkedHashSet<GrantedAuthority>(oAuth2User.getAuthorities());
        principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);

        return new DefaultOAuth2User(authorities, attrs, "login");
    }
}
