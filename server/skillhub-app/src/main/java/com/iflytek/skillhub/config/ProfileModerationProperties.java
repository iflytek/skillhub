package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.user.ProfileModerationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for profile moderation behavior.
 *
 * <p>Controls whether machine review and/or human review are enabled
 * when users update their profile. Effective defaults come from Spring configuration;
 * the current application default enables both checks unless overridden by environment.
 *
 * <p>Configuration combinations:
 * <pre>
 *   machine=false, human=false → changes apply immediately
 *   machine=true,  human=false → machine review only, pass = immediate effect
 *   machine=false, human=true  → skip machine review, enter human review queue
 *   machine=true,  human=true  → machine review first, then human review queue
 * </pre>
 *
 * <p>Implements {@link ProfileModerationConfig} to decouple domain layer from Spring Boot.
 *
 * @param machineReview whether to run machine review (e.g. sensitive word detection)
 * @param humanReview   whether to queue changes for human reviewer approval
 */
@ConfigurationProperties(prefix = "skillhub.profile.moderation")
public record ProfileModerationProperties(
        boolean machineReview,
        boolean humanReview
) implements ProfileModerationConfig {

    /** Returns true if any form of moderation is active. */
    public boolean isAnyModerationEnabled() {
        return machineReview || humanReview;
    }
}
