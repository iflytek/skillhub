package com.iflytek.skillhub.domain.user;

import java.util.Map;

/**
 * Pluggable moderation service for user profile changes.
 *
 * <p>Implementations are provided at the application layer and injected
 * into domain services. The open-source default is a no-op that always
 * approves; SaaS deployments can supply a machine-review implementation.
 */
public interface ProfileModerationService {

    /**
     * Evaluate proposed profile changes against moderation rules.
     *
     * @param userId  the user requesting the change
     * @param changes map of field name → new value (e.g. "displayName" → "new name")
     * @return moderation result indicating whether to approve, reject, or queue for review
     */
    ModerationResult moderate(String userId, Map<String, String> changes);
}
