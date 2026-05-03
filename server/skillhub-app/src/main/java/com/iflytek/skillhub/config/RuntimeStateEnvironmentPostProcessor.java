package com.iflytek.skillhub.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class RuntimeStateEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = RuntimeStatePropertyDefaults.resolveOverrides(environment);
        if (overrides.isEmpty()) {
            return;
        }
        MapPropertySource propertySource = new MapPropertySource(RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME, overrides);
        if (environment.getPropertySources().contains(RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().replace(RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        environment.getPropertySources().addFirst(propertySource);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
