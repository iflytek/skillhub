package com.iflytek.skillhub.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when user-authored code would run directly on the
 * host outside a development profile: with {@code skillhub.authoring.local-script}
 * enabled in {@code INLINE} execution mode, validation scripts and stdio MCP
 * commands execute as server subprocesses. Production deployments must use the
 * {@code DOCKER} execution mode (or disable local-script entirely).
 */
@Component
public class AuthoringStartupGuard implements ApplicationRunner {

    /** Profiles in which host-side execution is a deliberate development choice. */
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "dev", "test");

    private final AuthoringProperties properties;
    private final Environment environment;

    public AuthoringStartupGuard(AuthoringProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getLocalScript().isEnabled()) {
            return;
        }
        if (properties.getLocalScript().getExecutionMode()
                != AuthoringProperties.ScriptExecutionMode.INLINE) {
            return;
        }
        boolean nonProduction = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(NON_PRODUCTION_PROFILES::contains);
        if (nonProduction) {
            return;
        }
        throw new IllegalStateException("""
                skillhub.authoring.local-script.execution-mode=inline is not allowed in \
                production: user-authored scripts and stdio MCP commands would run \
                directly on the host. Set SKILLHUB_AUTHORING_LOCAL_SCRIPT_MODE=docker \
                (locked-down containers) or disable local-script.""");
    }
}
