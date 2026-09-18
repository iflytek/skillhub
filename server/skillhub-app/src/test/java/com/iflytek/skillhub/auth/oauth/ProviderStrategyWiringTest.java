package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.oauth.DingTalkOAuth2Constants;
import com.iflytek.skillhub.auth.oauth.DispatchingTokenResponseClient;
import com.iflytek.skillhub.auth.oauth.OAuthClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.ProviderAuthorizationRequestCustomizer;
import com.iflytek.skillhub.auth.oauth.ProviderOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.ProviderTokenResponseClient;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Loads the real application context to prove the provider strategy beans are constructible.
 *
 * <p>The unit tests for these classes call their package-visible constructors directly, so they
 * cannot catch Spring wiring faults: a component with two constructors and no {@code @Autowired}
 * marker compiles and unit-tests green, then fails at startup with "No default constructor found".
 * This test is the guard for that class of failure.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ProviderStrategyWiringTest {

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Autowired
    private DispatchingTokenResponseClient dispatchingTokenResponseClient;

    @Autowired
    private List<ProviderTokenResponseClient> tokenResponseClients;

    @Autowired
    private List<ProviderOAuth2UserService> userServices;

    @Autowired
    private List<ProviderAuthorizationRequestCustomizer> authorizationCustomizers;

    @Autowired
    private List<OAuthClaimsExtractor> claimsExtractors;

    @Test
    void dispatcherAndEveryProviderStrategyAreConstructible() {
        assertThat(dispatchingTokenResponseClient).isNotNull();

        // DingTalk needs all three strategy hooks; a missing bean would silently fall back to the
        // standard OAuth2 behaviour its endpoints reject.
        assertThat(tokenResponseClients)
                .extracting(ProviderTokenResponseClient::getProvider)
                .contains(DingTalkOAuth2Constants.REGISTRATION_ID);
        assertThat(authorizationCustomizers)
                .extracting(ProviderAuthorizationRequestCustomizer::getProvider)
                .contains(DingTalkOAuth2Constants.REGISTRATION_ID);
        assertThat(userServices)
                .extracting(ProviderOAuth2UserService::getProvider)
                .contains(DingTalkOAuth2Constants.REGISTRATION_ID, "feishu");
        assertThat(claimsExtractors)
                .extracting(OAuthClaimsExtractor::getProvider)
                .contains(DingTalkOAuth2Constants.REGISTRATION_ID, "feishu", "github");
    }

    @Test
    void providerKeysAreUniqueSoDispatchMapsCannotCollide() {
        // Collectors.toMap in the dispatchers throws on duplicate keys, which would break startup.
        assertThat(tokenResponseClients).extracting(ProviderTokenResponseClient::getProvider)
                .doesNotHaveDuplicates();
        assertThat(userServices).extracting(ProviderOAuth2UserService::getProvider)
                .doesNotHaveDuplicates();
        assertThat(authorizationCustomizers).extracting(ProviderAuthorizationRequestCustomizer::getProvider)
                .doesNotHaveDuplicates();
        assertThat(claimsExtractors).extracting(OAuthClaimsExtractor::getProvider)
                .doesNotHaveDuplicates();
    }
}
