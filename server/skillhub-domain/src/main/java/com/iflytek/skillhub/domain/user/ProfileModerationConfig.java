package com.iflytek.skillhub.domain.user;

/**
 * Domain-level abstraction for profile moderation configuration.
 *
 * <p>Decouples the domain service from Spring Boot's
 * {@code @ConfigurationProperties}. The app layer provides
 * the concrete implementation backed by application.yml.
 */
public interface ProfileModerationConfig {

    /** Whether machine review (e.g. sensitive word detection) is enabled. */
    boolean machineReview();

    /** Whether human review queue is enabled. */
    boolean humanReview();
}
