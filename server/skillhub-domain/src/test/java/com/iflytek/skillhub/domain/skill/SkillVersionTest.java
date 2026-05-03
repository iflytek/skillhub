package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillVersionTest {

    @Test
    void constructorAndLifecyclePopulateDefaults() {
        SkillVersion empty = new SkillVersion();

        assertThat(empty.getId()).isNull();
        assertThat(empty.getSkillId()).isNull();
        assertThat(empty.getVersion()).isNull();
        assertThat(empty.getStatus()).isNull();
        assertThat(empty.getChangelog()).isNull();
        assertThat(empty.getParsedMetadataJson()).isNull();
        assertThat(empty.getManifestJson()).isNull();
        assertThat(empty.getRequestedVisibility()).isNull();
        assertThat(empty.getFileCount()).isEqualTo(0);
        assertThat(empty.getTotalSize()).isEqualTo(0L);
        assertThat(empty.getPublishedAt()).isNull();
        assertThat(empty.isBundleReady()).isFalse();
        assertThat(empty.isDownloadReady()).isFalse();
        assertThat(empty.getYankedAt()).isNull();
        assertThat(empty.getYankedBy()).isNull();
        assertThat(empty.getYankReason()).isNull();
        assertThat(empty.getCreatedBy()).isNull();
        assertThat(empty.getCreatedAt()).isNull();

        SkillVersion version = new SkillVersion(7L, "1.2.3", "creator");

        assertThat(version.getSkillId()).isEqualTo(7L);
        assertThat(version.getVersion()).isEqualTo("1.2.3");
        assertThat(version.getCreatedBy()).isEqualTo("creator");
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.DRAFT);

        version.onCreate();

        assertThat(version.getCreatedAt()).isNotNull();
    }

    @Test
    void settersUpdateMutableFields() {
        SkillVersion version = new SkillVersion(7L, "1.2.3", "creator");
        Instant publishedAt = Instant.parse("2026-05-04T01:00:00Z");
        Instant yankedAt = Instant.parse("2026-05-04T02:00:00Z");

        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setChangelog("released");
        version.setParsedMetadataJson("{\"name\":\"demo\"}");
        version.setManifestJson("{\"schemaVersion\":\"1.0\"}");
        version.setRequestedVisibility(SkillVisibility.PUBLIC);
        version.setFileCount(3);
        version.setTotalSize(2048L);
        version.setPublishedAt(publishedAt);
        version.setBundleReady(true);
        version.setDownloadReady(true);
        version.setYankedAt(yankedAt);
        version.setYankedBy("moderator");
        version.setYankReason("policy");

        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(version.getChangelog()).isEqualTo("released");
        assertThat(version.getParsedMetadataJson()).isEqualTo("{\"name\":\"demo\"}");
        assertThat(version.getManifestJson()).isEqualTo("{\"schemaVersion\":\"1.0\"}");
        assertThat(version.getRequestedVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(version.getFileCount()).isEqualTo(3);
        assertThat(version.getTotalSize()).isEqualTo(2048L);
        assertThat(version.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(version.isBundleReady()).isTrue();
        assertThat(version.isDownloadReady()).isTrue();
        assertThat(version.getYankedAt()).isEqualTo(yankedAt);
        assertThat(version.getYankedBy()).isEqualTo("moderator");
        assertThat(version.getYankReason()).isEqualTo("policy");
    }
}
