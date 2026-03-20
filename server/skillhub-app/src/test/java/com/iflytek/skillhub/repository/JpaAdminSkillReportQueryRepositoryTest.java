package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JpaAdminSkillReportQueryRepositoryTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    private JpaAdminSkillReportQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaAdminSkillReportQueryRepository(skillRepository, namespaceRepository);
    }

    @Test
    void getSkillReportSummaries_joinsSkillAndNamespaceContext() {
        SkillReport report = new SkillReport(10L, 101L, "reporter-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 1L);
        ReflectionTestUtils.setField(report, "createdAt", Instant.parse("2026-03-20T02:00:00Z"));

        Skill skill = new Skill(101L, "skill-a", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Skill A");
        ReflectionTestUtils.setField(skill, "id", 10L);

        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 101L);

        given(skillRepository.findByIdIn(List.of(10L))).willReturn(List.of(skill));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));

        var response = repository.getSkillReportSummaries(List.of(report));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).namespace()).isEqualTo("team-a");
        assertThat(response.get(0).skillSlug()).isEqualTo("skill-a");
        assertThat(response.get(0).skillDisplayName()).isEqualTo("Skill A");
    }

    @Test
    void getSkillReportSummaries_toleratesMissingSkillContext() {
        SkillReport report = new SkillReport(10L, 101L, "reporter-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 2L);
        ReflectionTestUtils.setField(report, "createdAt", Instant.parse("2026-03-20T02:00:00Z"));

        given(skillRepository.findByIdIn(List.of(10L))).willReturn(List.of());

        var response = repository.getSkillReportSummaries(List.of(report));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).namespace()).isNull();
        assertThat(response.get(0).skillSlug()).isNull();
        assertThat(response.get(0).skillDisplayName()).isNull();
    }
}
