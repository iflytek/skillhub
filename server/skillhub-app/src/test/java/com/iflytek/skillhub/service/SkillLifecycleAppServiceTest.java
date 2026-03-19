package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

class SkillLifecycleAppServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
    private final SkillGovernanceService skillGovernanceService = mock(SkillGovernanceService.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    private final SkillPublishService skillPublishService = mock(SkillPublishService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final SkillSlugResolutionService skillSlugResolutionService = mock(SkillSlugResolutionService.class);
    private final SkillLifecycleAppService service = new SkillLifecycleAppService(
            namespaceRepository,
            skillVersionRepository,
            skillGovernanceService,
            reviewService,
            skillPublishService,
            auditLogService,
            skillSlugResolutionService
    );

    @Test
    void archiveSkill_resolvesNamespaceAndDelegatesLifecycleMutation() {
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 7L);
        Skill skill = new Skill(7L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 11L);
        skill.setStatus(com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillSlugResolutionService.resolve(7L, "demo-skill", "owner-1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .thenReturn(skill);
        when(skillGovernanceService.archiveSkill(eq(11L), eq("owner-1"), anyMap(), nullable(String.class), nullable(String.class), eq("cleanup")))
                .thenReturn(skill);

        var response = service.archiveSkill(
                "global",
                "demo-skill",
                new AdminSkillActionRequest("cleanup"),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(response.skillId()).isEqualTo(11L);
        assertThat(response.action()).isEqualTo("ARCHIVE");
        assertThat(response.status()).isEqualTo("ARCHIVED");
        verify(skillGovernanceService).archiveSkill(11L, "owner-1", Map.of(7L, NamespaceRole.OWNER), "127.0.0.1", "JUnit", "cleanup");
    }
}
