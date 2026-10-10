package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillSlugResolutionServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final SkillSlugResolutionService service = new SkillSlugResolutionService(skillRepository);

    @Test
    void prefersCurrentUsersOwnSkillWhenRequested() throws Exception {
        Skill publishedSkill = createSkill(1L, "demo", "user-2", 11L);
        Skill ownSkill = createSkill(2L, "demo", "user-1", 22L);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo")).thenReturn(List.of(publishedSkill, ownSkill));

        Skill resolved = service.resolve(1L, "demo", "user-1", SkillSlugResolutionService.Preference.CURRENT_USER);

        assertEquals(2L, resolved.getId());
    }

    @Test
    void prefersPublishedSkillForPublicInteractions() throws Exception {
        Skill ownDraft = createSkill(2L, "demo", "user-1", null);
        Skill publishedSkill = createSkill(1L, "demo", "user-2", 11L);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo")).thenReturn(List.of(ownDraft, publishedSkill));

        Skill resolved = service.resolve(1L, "demo", "user-1", SkillSlugResolutionService.Preference.PUBLISHED);

        assertEquals(1L, resolved.getId());
    }

    @Test
    void throwsWhenNoSkillMatchesSlug() {
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo")).thenReturn(List.of());

        assertThrows(DomainBadRequestException.class, () ->
                service.resolve(1L, "demo", "user-1", SkillSlugResolutionService.Preference.CURRENT_USER));
    }

    @Test
    void throwsWhenOnlyUnpublishedSkillsBelongToOtherUsers() throws Exception {
        Skill otherUsersDraft = createSkill(3L, "demo", "user-2", null);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo")).thenReturn(List.of(otherUsersDraft));

        assertThrows(DomainBadRequestException.class, () ->
                service.resolve(1L, "demo", null, SkillSlugResolutionService.Preference.CURRENT_USER));
    }

    @Test
    void throwsWhenOnlyPublishedSkillIsHiddenFromCurrentUser() throws Exception {
        Skill hiddenPublishedSkill = createSkill(4L, "demo", "user-2", 44L);
        hiddenPublishedSkill.setHidden(true);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo")).thenReturn(List.of(hiddenPublishedSkill));

        assertThrows(DomainBadRequestException.class, () ->
                service.resolve(1L, "demo", "user-9", SkillSlugResolutionService.Preference.CURRENT_USER));
    }

    private Skill createSkill(Long id, String slug, String ownerId, Long latestVersionId) throws Exception {
        Skill skill = new Skill(1L, slug, ownerId, SkillVisibility.PUBLIC);
        Field idField = Skill.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(skill, id);
        skill.setLatestVersionId(latestVersionId);
        return skill;
    }
}
