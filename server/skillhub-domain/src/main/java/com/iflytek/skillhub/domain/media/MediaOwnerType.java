package com.iflytek.skillhub.domain.media;

/**
 * Owner of a media asset. Bound owners use the singular form so the same media
 * row can target a skill version, a bundle version, or a promotion campaign.
 */
public enum MediaOwnerType {
    SKILL_VERSION,
    SKILL_BUNDLE_VERSION,
    PROMOTION_CAMPAIGN
}
