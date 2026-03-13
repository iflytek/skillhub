package com.iflytek.skillhub.domain.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.util.Optional;
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
    private AuditLogService auditLogService;

    private SkillGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new SkillGovernanceService(skillRepository, skillVersionRepository, auditLogService);
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
}
