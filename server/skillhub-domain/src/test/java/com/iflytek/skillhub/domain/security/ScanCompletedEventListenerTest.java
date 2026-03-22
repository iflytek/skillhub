package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanCompletedEventListenerTest {

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @InjectMocks
    private ScanCompletedEventListener listener;

    @Test
    void onScanCompleted_createsReviewTaskForScannedVersion() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);

        Skill skill = new Skill(20L, "demo-skill", "publisher-1", SkillVisibility.PUBLIC);
        setId(skill, 8L);

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));
        given(skillRepository.findById(8L)).willReturn(Optional.of(skill));

        listener.onScanCompleted(new ScanCompletedEvent(42L, SecurityVerdict.SAFE, 0));

        ArgumentCaptor<ReviewTask> reviewTaskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(reviewTaskCaptor.capture());
        ReviewTask reviewTask = reviewTaskCaptor.getValue();
        assertThat(reviewTask.getSkillVersionId()).isEqualTo(42L);
        assertThat(reviewTask.getNamespaceId()).isEqualTo(20L);
        assertThat(reviewTask.getSubmittedBy()).isEqualTo("publisher-1");
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
