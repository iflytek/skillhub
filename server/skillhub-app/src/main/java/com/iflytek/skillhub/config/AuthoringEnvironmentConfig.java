package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.authoring.service.ScriptEnvironmentPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires deployment configuration into the domain policy without a reverse module dependency. */
@Configuration
public class AuthoringEnvironmentConfig {

    @Bean
    public ScriptEnvironmentPolicy scriptEnvironmentPolicy(AuthoringProperties properties) {
        return new ScriptEnvironmentPolicy(() -> properties.getLocalScript().getEnvironmentAllowlist());
    }
}
