package com.iflytek.skillhub.auth.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccessPolicyFactoryTest {

    private static final OAuthClaims TEST_CLAIMS = new OAuthClaims(
            "github", "sub-1", "user@example.com", true, "user", Map.of()
    );

    @Test
    void defaultModeCreatesOpenAccessPolicy() {
        AccessPolicyFactory factory = new AccessPolicyFactory();

        AccessPolicy policy = factory.accessPolicy();

        assertThat(policy).isInstanceOf(OpenAccessPolicy.class);
        assertThat(policy.evaluate(TEST_CLAIMS)).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void emailDomainModeCreatesEmailDomainAccessPolicy() {
        AccessPolicyFactory factory = new AccessPolicyFactory();
        factory.setMode("EMAIL_DOMAIN");
        factory.setAllowedEmailDomains(List.of("example.com", "test.org"));

        AccessPolicy policy = factory.accessPolicy();

        assertThat(policy).isInstanceOf(EmailDomainAccessPolicy.class);
        OAuthClaims allowed = new OAuthClaims("github", "sub", "user@example.com", true, "user", Map.of());
        OAuthClaims denied = new OAuthClaims("github", "sub", "user@other.com", true, "user", Map.of());
        assertThat(policy.evaluate(allowed)).isEqualTo(AccessDecision.ALLOW);
        assertThat(policy.evaluate(denied)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void providerAllowlistModeCreatesProviderAllowlistAccessPolicy() {
        AccessPolicyFactory factory = new AccessPolicyFactory();
        factory.setMode("PROVIDER_ALLOWLIST");
        factory.setAllowedProviders(List.of("github", "gitlab"));

        AccessPolicy policy = factory.accessPolicy();

        assertThat(policy).isInstanceOf(ProviderAllowlistAccessPolicy.class);
        OAuthClaims allowed = new OAuthClaims("github", "sub", "any@example.com", true, "user", Map.of());
        OAuthClaims denied = new OAuthClaims("google", "sub", "any@example.com", true, "user", Map.of());
        assertThat(policy.evaluate(allowed)).isEqualTo(AccessDecision.ALLOW);
        assertThat(policy.evaluate(denied)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void subjectWhitelistModeCreatesSubjectWhitelistAccessPolicy() {
        AccessPolicyFactory factory = new AccessPolicyFactory();
        factory.setMode("SUBJECT_WHITELIST");
        factory.setWhitelistedSubjects(List.of("github:sub-1", "github:sub-2"));

        AccessPolicy policy = factory.accessPolicy();

        assertThat(policy).isInstanceOf(SubjectWhitelistAccessPolicy.class);
        OAuthClaims allowed = new OAuthClaims("github", "sub-1", "any@example.com", true, "user", Map.of());
        OAuthClaims denied = new OAuthClaims("github", "sub-3", "any@example.com", true, "user", Map.of());
        assertThat(policy.evaluate(allowed)).isEqualTo(AccessDecision.ALLOW);
        assertThat(policy.evaluate(denied)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void lowercaseModeIsNormalized() {
        AccessPolicyFactory factory = new AccessPolicyFactory();
        factory.setMode("email_domain");
        factory.setAllowedEmailDomains(List.of("example.com"));

        AccessPolicy policy = factory.accessPolicy();

        assertThat(policy).isInstanceOf(EmailDomainAccessPolicy.class);
    }
}
