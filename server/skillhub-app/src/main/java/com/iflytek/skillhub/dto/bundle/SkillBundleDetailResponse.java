package com.iflytek.skillhub.dto.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundle;
import com.iflytek.skillhub.domain.bundle.SkillBundleType;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersion;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SkillBundleDetailResponse(
        Long id,
        Long namespaceId,
        String slug,
        String displayName,
        SkillBundleType type,
        String summary,
        long downloadCount,
        int starCount,
        BigDecimal ratingAvg,
        int ratingCount,
        int commentCount,
        Long latestVersionId,
        VersionView version,
        List<ItemView> items,
        Instant updatedAt
) {
    public record VersionView(Long id, String version, SkillBundleVersionStatus status, Instant publishedAt) {
        public static VersionView from(SkillBundleVersion v) {
            return new VersionView(v.getId(), v.getVersion(), v.getStatus(), v.getPublishedAt());
        }
    }

    public record ItemView(
            Long skillId,
            String namespaceSlug,
            String skillSlug,
            String displayName,
            String version,
            String roleDescription,
            boolean required,
            int installOrder,
            String detailUrl
    ) {}

    public static SkillBundleDetailResponse build(SkillBundle bundle,
                                                  SkillBundleVersion version,
                                                  List<ItemView> items) {
        return new SkillBundleDetailResponse(
                bundle.getId(), bundle.getNamespaceId(), bundle.getSlug(),
                bundle.getDisplayName(), bundle.getBundleType(), bundle.getSummary(),
                bundle.getDownloadCount(), bundle.getStarCount(),
                bundle.getRatingAvg(), bundle.getRatingCount(), bundle.getCommentCount(),
                bundle.getLatestVersionId(),
                version == null ? null : VersionView.from(version),
                items,
                bundle.getUpdatedAt()
        );
    }
}
