package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.user.ModerationResult;
import com.iflytek.skillhub.domain.user.ProfileModerationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Default (no-op) profile moderation service for open-source deployments.
 *
 * <p>Always returns {@link ModerationResult#approved()}, meaning profile
 * changes take effect immediately without any review.
 *
 * <p>SaaS deployments can override by providing their own
 * {@link ProfileModerationService} bean annotated with {@code @Primary}.
 */
@Service
public class NoOpProfileModerationService implements ProfileModerationService {

    @Override
    public ModerationResult moderate(String userId, Map<String, String> changes) {
        return ModerationResult.approved();
    }
}
