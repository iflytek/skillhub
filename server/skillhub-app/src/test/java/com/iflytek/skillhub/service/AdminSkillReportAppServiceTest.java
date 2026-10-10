package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.report.SkillReportStatus;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import com.iflytek.skillhub.repository.AdminSkillReportQueryRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSkillReportAppServiceTest {

    @Mock
    private SkillReportRepository skillReportRepository;

    @Mock
    private AdminSkillReportQueryRepository adminSkillReportQueryRepository;

    private AdminSkillReportAppService service;

    @BeforeEach
    void setUp() {
        service = new AdminSkillReportAppService(skillReportRepository, adminSkillReportQueryRepository);
    }

    @Test
    void listReports_usesQueryRepositoryForSummaryAssembly() {
        SkillReport report = new SkillReport(10L, 101L, "reporter-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 1L);
        ReflectionTestUtils.setField(report, "createdAt", Instant.parse("2026-03-20T02:00:00Z"));

        given(skillReportRepository.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));
        given(adminSkillReportQueryRepository.getSkillReportSummaries(List.of(report)))
                .willReturn(List.of(new AdminSkillReportSummaryResponse(
                        1L,
                        10L,
                        "team-a",
                        "skill-a",
                        "Skill A",
                        "reporter-1",
                        "Spam",
                        "details",
                        "PENDING",
                        null,
                        null,
                        Instant.parse("2026-03-20T02:00:00Z"),
                        null
                )));

        var response = service.listReports(null, 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).namespace()).isEqualTo("team-a");
    }
}
