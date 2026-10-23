package com.iflytek.skillhub.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingValidator;
import com.iflytek.skillhub.service.authoring.adapter.LocalScriptRuntimeAdapter;
import com.iflytek.skillhub.service.authoring.adapter.InlineScriptCommandBuilder;
import static org.assertj.core.api.Assertions.*;

class AuthoringEnvironmentConfigTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthoringProperties.class)
    static class PropertiesConfiguration { }

    @Test void springContainerInjectsTheSharedPolicyIntoBothEntryPoints() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("dummy-deployment", Map.of(
                    "skillhub.authoring.local-script.environment-allowlist", "AUTHORING_DEMO")));
            context.register(PropertiesConfiguration.class, AuthoringEnvironmentConfig.class,
                    RuntimeBindingValidator.class, LocalScriptRuntimeAdapter.class, InlineScriptCommandBuilder.class);
            context.refresh();
            var validator = context.getBean(RuntimeBindingValidator.class);
            assertThat(context.getBean(LocalScriptRuntimeAdapter.class).agentType()).isEqualTo("local-script");
            var config = Map.<String, Object>of("envAllowlist", List.of("AUTHORING_DEMO"));
            assertThat(validator.validate("local-script", config, null, null)).isEmpty();
            context.getBean(AuthoringProperties.class).getLocalScript().setEnvironmentAllowlist(List.of());
            assertThat(validator.validate("local-script", config, null, null)).hasSize(1);
        }
    }
    @Test void defaultPropertiesDenyAllAndBeanSharesEffectiveSettings() {
        var properties = new AuthoringProperties();
        var policy = new AuthoringEnvironmentConfig().scriptEnvironmentPolicy(properties);
        assertThatThrownBy(() -> policy.authorize(List.of("AUTHORING_DEMO"))).isInstanceOf(IllegalArgumentException.class);
        properties.getLocalScript().setEnvironmentAllowlist(List.of("AUTHORING_DEMO"));
        assertThat(policy.authorize(List.of("AUTHORING_DEMO"))).containsExactly("AUTHORING_DEMO");
        properties.getLocalScript().setEnvironmentAllowlist(null);
        assertThatThrownBy(() -> policy.authorize(List.of("AUTHORING_DEMO"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void commaSeparatedSettingBindsWithoutChangingDockerMode() {
        var source = new MapConfigurationPropertySource(Map.of(
                "skillhub.authoring.local-script.environment-allowlist", "AUTHORING_DEMO,AUTHORING_OTHER",
                "skillhub.authoring.local-script.execution-mode", "docker"));
        var properties = new Binder(source).bind("skillhub.authoring", Bindable.of(AuthoringProperties.class)).get();
        assertThat(properties.getLocalScript().getExecutionMode()).isEqualTo(AuthoringProperties.ScriptExecutionMode.DOCKER);
        assertThat(properties.getLocalScript().getEnvironmentAllowlist()).containsExactly("AUTHORING_DEMO", "AUTHORING_OTHER");
        assertThat(new AuthoringEnvironmentConfig().scriptEnvironmentPolicy(properties).authorize(List.of("AUTHORING_DEMO")))
                .containsExactly("AUTHORING_DEMO");
    }
    @Test void explicitEmptySettingBindsToDenyAll() {
        var source = new MapConfigurationPropertySource(Map.of(
                "skillhub.authoring.local-script.environment-allowlist", ""));
        var properties = new Binder(source).bind("skillhub.authoring", Bindable.of(AuthoringProperties.class)).get();
        assertThat(properties.getLocalScript().getEnvironmentAllowlist()).isEmpty();
    }
}
