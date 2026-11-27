package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillPublishAppServiceChangelogTest {

    @Mock private SkillPublishService publications;
    @Mock private SkillAvatarUploadService uploads;
    @Mock private SkillAvatarAppService avatars;
    @Mock private SkillMetadataParser metadata;
    @Mock private ReviewTaskRepository reviews;
    @Mock private SkillVersionRepository versions;

    private SkillPublishAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillPublishAppService(publications, uploads, avatars, metadata);
        service.setReviews(reviews);
        service.setVersions(versions);
    }

    @Test
    void publish_attachesTheReleaseNoteToTheNewVersion() {
        SkillVersion version = new SkillVersion(1L, "1.2.0", "publisher");
        given(publications.publishFromEntries("global", List.of(), "publisher", SkillVisibility.PUBLIC,
                Set.of(), false)).willReturn(new SkillPublishService.PublishResult(1L, "demo", version));
        given(reviews.findBySkillVersionIdAndStatus(any(), any())).willReturn(Optional.empty());

        service.publish("global", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), false,
                SkillPublishAppService.AvatarAction.KEEP, null, null, null, null, null, false,
                new AuditRequestContext("127.0.0.1", "test"), null, "  Fixed the parser and added a test  ");

        assertThat(version.getChangelog()).isEqualTo("Fixed the parser and added a test");
        verify(versions).save(version);
    }

    @Test
    void publish_leavesTheFieldEmptyWhenNoReleaseNoteWasSent() {
        SkillVersion version = new SkillVersion(1L, "1.2.0", "publisher");
        given(publications.publishFromEntries("global", List.of(), "publisher", SkillVisibility.PUBLIC,
                Set.of(), false)).willReturn(new SkillPublishService.PublishResult(1L, "demo", version));
        given(reviews.findBySkillVersionIdAndStatus(any(), any())).willReturn(Optional.empty());

        service.publish("global", List.of(), "publisher", SkillVisibility.PUBLIC, Set.of(), false,
                SkillPublishAppService.AvatarAction.KEEP, null, null, null, null, null, false,
                new AuditRequestContext("127.0.0.1", "test"), (Set<String>) null, null);

        assertThat(version.getChangelog()).isNull();
        verify(versions, org.mockito.Mockito.never()).save(any());
    }
}
