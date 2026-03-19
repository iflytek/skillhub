package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.user.ProfileModerationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for profile moderation behavior.
 *
 * <p>Controls whether machine review and/or human review are enabled
 * when users update their profile. Both default to {@code false} (open-source mode).
 *
 * <p>Configuration combinations:
 * <pre>
 *   machine=false, human=false → changes apply immediately (open-source default)
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

    /** Default constructor — both switches off (open-source mode). */
    public ProfileModerationProperties() {
        this(false, false);
    }

    /** Returns true if any form of moderation is active. */
    public boolean isAnyModerationEnabled() {
        return machineReview || humanReview;
    }
}
