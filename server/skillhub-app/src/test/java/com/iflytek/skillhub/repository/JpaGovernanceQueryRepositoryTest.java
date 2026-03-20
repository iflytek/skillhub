package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaGovernanceQueryRepositoryTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private JpaGovernanceQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaGovernanceQueryRepository(
                skillRepository,
                skillVersionRepository,
                namespaceRepository,
                userAccountRepository
        );
    }

    @Test
    void getReviewTaskResponses_assemblesReviewReadModel() {
        ReviewTask task = new ReviewTask(101L, 11L, "submitter");
        setField(task, "id", 1L);
        setField(task, "reviewedBy", "reviewer");
        setField(task, "reviewComment", "LGTM");
        setField(task, "submittedAt", Instant.parse("2026-03-20T02:00:00Z"));
        setField(task, "reviewedAt", Instant.parse("2026-03-20T03:00:00Z"));
        setField(task, "status", ReviewTaskStatus.APPROVED);

        SkillVersion version = new SkillVersion(201L, "1.2.0", "submitter");
        setField(version, "id", 101L);
        Skill skill = new Skill(11L, "skill-a", "submitter", SkillVisibility.PUBLIC);
        setField(skill, "id", 201L);
        Namespace namespace = new Namespace("team-a", "Team A", "submitter");
        setField(namespace, "id", 11L);
        UserAccount submitter = new UserAccount("submitter", "Submitter", "submitter@example.com", null);
        UserAccount reviewer = new UserAccount("reviewer", "Reviewer", "reviewer@example.com", null);

        given(skillVersionRepository.findByIdIn(List.of(101L))).willReturn(List.of(version));
        given(skillRepository.findByIdIn(List.of(201L))).willReturn(List.of(skill));
        given(namespaceRepository.findByIdIn(List.of(11L))).willReturn(List.of(namespace));
        given(userAccountRepository.findByIdIn(List.of("submitter", "reviewer")))
                .willReturn(List.of(submitter, reviewer));

        var responses = repository.getReviewTaskResponses(List.of(task));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).namespace()).isEqualTo("team-a");
        assertThat(responses.get(0).skillSlug()).isEqualTo("skill-a");
        assertThat(responses.get(0).version()).isEqualTo("1.2.0");
        assertThat(responses.get(0).submittedByName()).isEqualTo("Submitter");
        assertThat(responses.get(0).reviewedByName()).isEqualTo("Reviewer");
    }

    @Test
    void getPromotionResponses_assemblesPromotionReadModel() {
        PromotionRequest request = new PromotionRequest(201L, 101L, 12L, "submitter");
        setField(request, "id", 2L);
        setField(request, "targetSkillId", 301L);
        setField(request, "reviewedBy", "reviewer");
        setField(request, "reviewComment", "Ship it");
        setField(request, "submittedAt", Instant.parse("2026-03-20T02:00:00Z"));
        setField(request, "reviewedAt", Instant.parse("2026-03-20T03:00:00Z"));
        setField(request, "status", ReviewTaskStatus.APPROVED);

        SkillVersion version = new SkillVersion(201L, "1.2.0", "submitter");
        setField(version, "id", 101L);
        Skill skill = new Skill(11L, "skill-a", "submitter", SkillVisibility.PUBLIC);
        setField(skill, "id", 201L);
        Namespace sourceNamespace = new Namespace("team-a", "Team A", "submitter");
        setField(sourceNamespace, "id", 11L);
        Namespace targetNamespace = new Namespace("global", "Global", "submitter");
        setField(targetNamespace, "id", 12L);
        UserAccount submitter = new UserAccount("submitter", "Submitter", "submitter@example.com", null);
        UserAccount reviewer = new UserAccount("reviewer", "Reviewer", "reviewer@example.com", null);

        given(skillRepository.findByIdIn(List.of(201L))).willReturn(List.of(skill));
        given(skillVersionRepository.findByIdIn(List.of(101L))).willReturn(List.of(version));
        given(namespaceRepository.findByIdIn(List.of(12L, 11L))).willReturn(List.of(targetNamespace, sourceNamespace));
        given(userAccountRepository.findByIdIn(List.of("submitter", "reviewer")))
                .willReturn(List.of(submitter, reviewer));

        var responses = repository.getPromotionResponses(List.of(request));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).sourceNamespace()).isEqualTo("team-a");
        assertThat(responses.get(0).targetNamespace()).isEqualTo("global");
        assertThat(responses.get(0).sourceSkillSlug()).isEqualTo("skill-a");
        assertThat(responses.get(0).submittedByName()).isEqualTo("Submitter");
        assertThat(responses.get(0).reviewedByName()).isEqualTo("Reviewer");
    }

    @Test
    void getReportInboxItems_toleratesMissingSkillButKeepsNamespaceContext() {
        SkillReport report = new SkillReport(201L, 11L, "reporter", "Spam", "details");
        setField(report, "id", 3L);
        setField(report, "createdAt", Instant.parse("2026-03-20T02:00:00Z"));

        Namespace namespace = new Namespace("team-a", "Team A", "submitter");
        setField(namespace, "id", 11L);

        given(skillRepository.findByIdIn(List.of(201L))).willReturn(List.of());
        given(namespaceRepository.findByIdIn(List.of(11L))).willReturn(List.of(namespace));

        var responses = repository.getReportInboxItems(List.of(report));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).type()).isEqualTo("REPORT");
        assertThat(responses.get(0).title()).isEqualTo("Unknown target");
        assertThat(responses.get(0).namespace()).isEqualTo("team-a");
        assertThat(responses.get(0).subtitle()).isEqualTo("Spam");
    }

    @Test
    void getReviewInboxItems_toleratesMissingVersionContext() {
        ReviewTask task = new ReviewTask(101L, 11L, "submitter");
        setField(task, "id", 4L);
        setField(task, "submittedAt", Instant.parse("2026-03-20T04:00:00Z"));

        given(skillVersionRepository.findByIdIn(List.of(101L))).willReturn(List.of());

        var responses = repository.getReviewInboxItems(List.of(task));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).type()).isEqualTo("REVIEW");
        assertThat(responses.get(0).title()).isEqualTo("Unknown target");
        assertThat(responses.get(0).namespace()).isNull();
        assertThat(responses.get(0).skillSlug()).isNull();
        assertThat(responses.get(0).subtitle()).isEqualTo("Pending review");
    }

    @Test
    void getPromotionInboxItems_toleratesMissingSourceAndTargetContext() {
        PromotionRequest request = new PromotionRequest(201L, 101L, 12L, "submitter");
        setField(request, "id", 5L);
        setField(request, "submittedAt", Instant.parse("2026-03-20T05:00:00Z"));

        given(skillRepository.findByIdIn(List.of(201L))).willReturn(List.of());
        given(skillVersionRepository.findByIdIn(List.of(101L))).willReturn(List.of());
        given(namespaceRepository.findByIdIn(List.of(12L))).willReturn(List.of());

        var responses = repository.getPromotionInboxItems(List.of(request));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).type()).isEqualTo("PROMOTION");
        assertThat(responses.get(0).title()).isEqualTo("Unknown target");
        assertThat(responses.get(0).namespace()).isNull();
        assertThat(responses.get(0).skillSlug()).isNull();
        assertThat(responses.get(0).subtitle()).isEqualTo("Pending promotion");
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
