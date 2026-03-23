package com.iflytek.skillhub.domain.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ScanCompletedEventListenerTest {

    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void onScanCompleted_createsReviewTaskAndPublishesReviewSubmittedEvent() throws Exception {
        ScanCompletedEventListener listener = new ScanCompletedEventListener(
                skillVersionRepository,
                skillRepository,
                reviewTaskRepository,
                eventPublisher
        );
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setField(version, "id", 42L);
        Skill skill = new Skill(5L, "demo-skill", "publisher-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 8L);

        when(skillVersionRepository.findById(42L)).thenReturn(Optional.of(version));
        when(skillRepository.findById(8L)).thenReturn(Optional.of(skill));
        when(reviewTaskRepository.save(org.mockito.ArgumentMatchers.any(ReviewTask.class)))
                .thenAnswer(invocation -> {
                    ReviewTask task = invocation.getArgument(0);
                    setField(task, "id", 100L);
                    return task;
                });

        listener.onScanCompleted(new ScanCompletedEvent(42L, SecurityVerdict.SAFE, 0));

        ArgumentCaptor<ReviewTask> reviewTaskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(reviewTaskCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reviewTaskCaptor.getValue().getSkillVersionId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(reviewTaskCaptor.getValue().getNamespaceId()).isEqualTo(5L);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReviewSubmittedEvent submittedEvent = (ReviewSubmittedEvent) eventCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(submittedEvent.reviewId()).isEqualTo(100L);
        org.assertj.core.api.Assertions.assertThat(submittedEvent.skillId()).isEqualTo(8L);
        org.assertj.core.api.Assertions.assertThat(submittedEvent.versionId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(submittedEvent.submitterId()).isEqualTo("publisher-1");
        org.assertj.core.api.Assertions.assertThat(submittedEvent.namespaceId()).isEqualTo(5L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
