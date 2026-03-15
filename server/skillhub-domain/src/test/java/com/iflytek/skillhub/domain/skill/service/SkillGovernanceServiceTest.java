package com.iflytek.skillhub.domain.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.Optional;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillGovernanceServiceTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new SkillGovernanceService(
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                objectStorageService,
                auditLogService,
                eventPublisher
        );
    }

    @Test
    void hideSkill_marksSkillHidden() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));
        given(skillRepository.save(skill)).willReturn(skill);

        Skill result = service.hideSkill(10L, "admin", "127.0.0.1", "JUnit", "policy");

        assertThat(result.isHidden()).isTrue();
        assertThat(result.getHiddenBy()).isEqualTo("admin");
        verify(auditLogService).record("admin", "HIDE_SKILL", "SKILL", 10L, null, "127.0.0.1", "JUnit", "{\"reason\":\"policy\"}");
    }

    @Test
    void archiveSkill_marksSkillArchived() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));
        given(skillRepository.save(skill)).willReturn(skill);

        Skill result = service.archiveSkill(10L, "owner", Map.of(), "127.0.0.1", "JUnit", "cleanup");

        assertThat(result.getStatus()).isEqualTo(SkillStatus.ARCHIVED);
        verify(auditLogService).record("owner", "ARCHIVE_SKILL", "SKILL", 10L, null, "127.0.0.1", "JUnit", "{\"reason\":\"cleanup\"}");
        verify(eventPublisher).publishEvent(any(SkillStatusChangedEvent.class));
    }

    @Test
    void unarchiveSkill_restoresActiveStatus() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setStatus(SkillStatus.ARCHIVED);
        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));
        given(skillRepository.save(skill)).willReturn(skill);

        Skill result = service.unarchiveSkill(10L, "owner", Map.of(), "127.0.0.1", "JUnit");

        assertThat(result.getStatus()).isEqualTo(SkillStatus.ACTIVE);
        verify(auditLogService).record("owner", "UNARCHIVE_SKILL", "SKILL", 10L, null, "127.0.0.1", "JUnit", null);
        verify(eventPublisher).publishEvent(any(SkillStatusChangedEvent.class));
    }

    @Test
    void archiveSkill_requiresOwnerOrNamespaceAdmin() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));

        assertThrows(DomainForbiddenException.class,
                () -> service.archiveSkill(10L, "other", Map.of(1L, NamespaceRole.MEMBER), "127.0.0.1", "JUnit", null));
    }

    @Test
    void yankVersion_setsYankedStatus() {
        SkillVersion version = new SkillVersion(2L, "1.0.0", "owner");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        given(skillVersionRepository.findById(22L)).willReturn(Optional.of(version));
        given(skillVersionRepository.save(version)).willReturn(version);

        SkillVersion result = service.yankVersion(22L, "admin", "127.0.0.1", "JUnit", "broken");

        assertThat(result.getStatus()).isEqualTo(SkillVersionStatus.YANKED);
        assertThat(result.getYankedBy()).isEqualTo("admin");
        verify(auditLogService).record("admin", "YANK_SKILL_VERSION", "SKILL_VERSION", 22L, null, "127.0.0.1", "JUnit", "{\"reason\":\"broken\"}");
    }

    @Test
    void deleteVersion_removesDraftFilesAndBundle() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        setField(skill, "id", 1L);
        SkillVersion version = new SkillVersion(2L, "1.0.0", "owner");
        setField(version, "id", 2L);
        version.setStatus(SkillVersionStatus.DRAFT);
        SkillFile readme = new SkillFile(version.getId(), "README.md", 10L, "text/markdown", "sha1", "skills/demo/readme");
        SkillFile icon = new SkillFile(version.getId(), "icon.png", 20L, "image/png", "sha2", "skills/demo/icon");
        given(skillFileRepository.findByVersionId(version.getId())).willReturn(java.util.List.of(readme, icon));

        service.deleteVersion(skill, version, "owner", Map.of(), "127.0.0.1", "JUnit");

        verify(objectStorageService).deleteObjects(argThat(keys ->
                keys.size() == 2
                        && keys.contains("skills/demo/readme")
                        && keys.contains("skills/demo/icon")));
        verify(objectStorageService).deleteObject("packages/1/2/bundle.zip");
        verify(skillFileRepository).deleteByVersionId(2L);
        verify(skillVersionRepository).delete(version);
        verify(auditLogService).record("owner", "DELETE_SKILL_VERSION", "SKILL_VERSION", 2L, null, "127.0.0.1", "JUnit", "{\"version\":\"1.0.0\"}");
    }

    @Test
    void deleteVersion_rejectsPublishedVersion() {
        Skill skill = new Skill(1L, "demo", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        setField(skill, "id", 1L);
        SkillVersion version = new SkillVersion(2L, "1.0.0", "owner");
        setField(version, "id", 2L);
        version.setStatus(SkillVersionStatus.PUBLISHED);

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteVersion(skill, version, "owner", Map.of(), "127.0.0.1", "JUnit"));

        verify(skillVersionRepository, never()).delete(any());
        verify(objectStorageService, never()).deleteObject(any());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
