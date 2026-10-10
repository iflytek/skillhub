package com.iflytek.skillhub.domain.skill;

/**
 * Defines whether a skill version can be installed through public download
 * paths. Storage object presence is checked later by the download service so
 * fallback bundle behavior stays separate from domain publication state.
 */
public final class SkillInstallability {
    private SkillInstallability() {
    }

    public static boolean isInstallableVersion(SkillVersion version) {
        return version != null
                && version.getStatus() == SkillVersionStatus.PUBLISHED
                && version.isDownloadReady()
                && version.getYankedAt() == null;
    }
}
