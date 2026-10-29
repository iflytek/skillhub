package com.iflytek.skillhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceAuthConfigurationTest {

    @Test
    void unsetOverrideUsesPublicBaseUrl() throws IOException {
        assertVerificationUri(Map.of("SKILLHUB_PUBLIC_BASE_URL", "https://skills.example.com"),
                "https://skills.example.com/device");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankOverrideUsesPublicBaseUrl(String override) throws IOException {
        assertVerificationUri(Map.of("SKILLHUB_PUBLIC_BASE_URL", "https://skills.example.com/portal/",
                        "DEVICE_AUTH_VERIFICATION_URI", override),
                "https://skills.example.com/portal/device");
    }

    @Test
    void absentPublicUrlKeepsSameOriginDefault() throws IOException {
        assertVerificationUri(Map.of(), "/device");
        assertVerificationUri(Map.of("DEVICE_AUTH_VERIFICATION_URI", " ",
                "SKILLHUB_PUBLIC_BASE_URL", " "), "/device");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://login.example.com/custom/verify?source=cli#confirm", "/portal/device"})
    void validOverrideIsPreserved(String override) throws IOException {
        assertVerificationUri(Map.of("SKILLHUB_PUBLIC_BASE_URL", "https://skills.example.com",
                "DEVICE_AUTH_VERIFICATION_URI", override), override);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//other.example.com/device", "https:///device",
            "https://user:secret@example.com/device", "https://skills.example.com/invalid path"})
    void invalidOverrideFailsContextWithoutEchoingItsValue(String override) throws IOException {
        run(Map.of("DEVICE_AUTH_VERIFICATION_URI", override), context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(context.getStartupFailure()).hasStackTraceContaining("skillhub.device-auth.verification-uri")
                    .hasStackTraceContaining("HTTP(S)")
                    .hasStackTraceContaining("root-relative");
            assertThat(context.getStartupFailure().getMessage()).doesNotContain(override);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-url", "https:///portal", "https://skills.example.com/?query=value",
            "https://skills.example.com/#fragment"})
    void invalidPublicUrlFailsInsteadOfIssuingBrokenCodes(String baseUrl) throws IOException {
        run(Map.of("SKILLHUB_PUBLIC_BASE_URL", baseUrl, "DEVICE_AUTH_VERIFICATION_URI", ""), context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("skillhub.public.base-url");
            assertThat(context.getStartupFailure().getMessage()).doesNotContain(baseUrl);
        });
    }

    private void assertVerificationUri(Map<String, Object> variables, String expected) throws IOException {
        run(variables, context -> {
            assertThat(context).hasNotFailed();
            var response = context.getBean(DeviceAuthService.class).generateDeviceCode();
            assertThat(response.verificationUri()).isEqualTo(expected);
            assertThat(response.deviceCode()).isNotBlank();
            assertThat(response.userCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
            assertThat(response.expiresIn()).isEqualTo(900);
            assertThat(response.interval()).isEqualTo(5);
        });
    }

    @SuppressWarnings("unchecked")
    private void run(Map<String, Object> variables, Consumer<AssertableApplicationContext> assertions)
            throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    var environment = context.getEnvironment();
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", variables));
                    sources.forEach(environment.getPropertySources()::addLast);
                })
                .withBean(RedisTemplate.class, () -> redis)
                .withBean(ApiTokenService.class, () -> mock(ApiTokenService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(DeviceAuthService.class)
                .run(assertions::accept);
    }
}
