package com.iflytek.skillhub.service.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CliSkillAppServiceChangelogTest {

    @Mock private SkillSearchAppService search;
    @Mock private SkillQueryService query;
    @Mock private SkillDownloadService download;
    @Mock private SkillDeleteAppService delete;
    @Mock private SkillPublishService publications;
    @Mock private SkillVersionRepository versions;

    private CliSkillAppService service;

    @BeforeEach
    void setUp() {
        service = new CliSkillAppService(search, query, download, delete, publications);
        service.setVersions(versions);
    }

    @Test
    void publish_storesTheReleaseNoteOnTheCreatedVersion() {
        SkillVersion version = new SkillVersion(9L, "1.1.0", "publisher");
        given(publications.publishFromEntries("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), false))
                .willReturn(new SkillPublishService.PublishResult(9L, "demo", version));

        service.publish("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), null, "  CLI note  ");

        assertThat(version.getChangelog()).isEqualTo("CLI note");
        verify(versions).save(version);
    }

    @Test
    void publish_reportsTheVersionStatusForTheCli() {
        SkillVersion version = new SkillVersion(9L, "1.1.0", "publisher");
        version.setStatus(com.iflytek.skillhub.domain.skill.SkillVersionStatus.PENDING_REVIEW);
        given(publications.publishFromEntries("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), false))
                .willReturn(new SkillPublishService.PublishResult(9L, "demo", version));

        var response = service.publish("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), null, null);

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void publish_withoutNoteDoesNotTouchTheVersion() {
        SkillVersion version = new SkillVersion(9L, "1.1.0", "publisher");
        given(publications.publishFromEntries("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), false))
                .willReturn(new SkillPublishService.PublishResult(9L, "demo", version));

        service.publish("team", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), null, null);

        assertThat(version.getChangelog()).isNull();
        verify(versions, never()).save(any());
    }
}
