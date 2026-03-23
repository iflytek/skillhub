package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkillScannerPropertiesBindingTest {

    @Test
    void defaultConfig_disablesScannerByDefault() throws IOException {
        SkillScannerProperties properties = bindProperties(
                List.of("application.yml"),
                Map.of()
        );

        assertFalse(properties.isEnabled());
        assertEquals("local", properties.getMode());
        assertEquals("http://localhost:8000", properties.getBaseUrl());
    }

    @Test
    void environmentVariables_overrideScannerDefaults() throws IOException {
        SkillScannerProperties properties = bindProperties(
                List.of("application-local.yml", "application.yml"),
                Map.of(
                        "SKILLHUB_SECURITY_SCANNER_ENABLED", "true",
                        "SKILLHUB_SECURITY_SCANNER_MODE", "upload",
                        "SKILLHUB_SECURITY_SCANNER_URL", "http://scanner.internal:9000",
                        "SKILLHUB_SCANNER_USE_LLM", "true",
                        "SKILLHUB_SCANNER_LLM_PROVIDER", "openai",
                        "SKILLHUB_SCANNER_USE_BEHAVIORAL", "true"
                )
        );

        assertEquals(true, properties.isEnabled());
        assertEquals("upload", properties.getMode());
        assertEquals("http://scanner.internal:9000", properties.getBaseUrl());
        assertEquals(true, properties.getAnalyzers().isLlm());
        assertEquals("openai", properties.getAnalyzers().getLlmProvider());
        assertEquals(true, properties.getAnalyzers().isBehavioral());
    }

    private SkillScannerProperties bindProperties(List<String> resourceNames,
                                                  Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", envVars));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : resourceNames) {
            List<org.springframework.core.env.PropertySource<?>> propertySources = loader.load(
                    resourceName,
                    new ClassPathResource(resourceName)
            );
            for (org.springframework.core.env.PropertySource<?> propertySource : propertySources) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        ConfigurationPropertySources.attach(environment);

        return Binder.get(environment)
                .bind("skillhub.security.scanner", SkillScannerProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind skill scanner properties"));
    }
}
