package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationPageResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationSummaryResponse;
import com.iflytek.skillhub.repository.SkillSuiteBundleOperationQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleOperationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private SkillSuiteBundleMemberResultRepository memberRepository;
    private NamespaceRepository namespaceRepository;
    private SkillRepository skillRepository;
    private SkillSuiteVersionRepository suiteVersionRepository;
    private SkillSuiteBundleOperationQueryRepository operationQueryRepository;
    private SkillSuiteBundleOperationQueryService service;

    @BeforeEach
    void setUp() {
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        namespaceRepository = mock(NamespaceRepository.class);
        skillRepository = mock(SkillRepository.class);
        suiteVersionRepository = mock(SkillSuiteVersionRepository.class);
        operationQueryRepository = mock(SkillSuiteBundleOperationQueryRepository.class);
        service = new SkillSuiteBundleOperationQueryService(
                operationRepository, memberRepository, namespaceRepository,
                skillRepository, suiteVersionRepository, new VisibilityChecker(), operationQueryRepository);
    }

    @Test
    void ownerReceivesRedactedOperationAndMemberProgress() {
        SkillSuiteBundleExecutionOperation operation = operation();
        Namespace namespace = mock(Namespace.class);
        when(namespace.getSlug()).thenReturn("global");
        when(namespace.getId()).thenReturn(1L);
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(namespaceRepository.findBySlugIn(List.of("global"))).thenReturn(List.of(namespace));
        when(memberRepository.findByOperationIdOrderByPosition("operation-1"))
                .thenReturn(List.of(member()));
        Skill skill = skill(10L, 1L, "other", SkillVisibility.PUBLIC);
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(skill));

        var response = service.get("operation-1", "actor", Map.of(), Set.of());

        assertThat(response.targetCoordinate()).isEqualTo("@global/suite");
        assertThat(response.targetNamespaceId()).isEqualTo(1L);
        assertThat(response.members()).singleElement().satisfies(member -> {
            assertThat(member.coordinate()).isEqualTo("@global/member");
            assertThat(member.packagePath()).isEqualTo("members/member");
            assertThat(member.version()).isEqualTo("1.0.0");
            assertThat(member.redacted()).isFalse();
        });
        assertThat(response.toString())
                .doesNotContain("actor")
                .doesNotContain("request-1")
                .doesNotContain("temporary/archive.zip")
                .doesNotContain("internal-plan-value");
    }

    @Test
    void namespaceAdminCanReadButUnrelatedCallerCannotProbeOperationId() {
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation()));
        Namespace namespace = mock(Namespace.class);
        when(namespace.getSlug()).thenReturn("global");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));

        assertThat(service.get(
                "operation-1", "admin", Map.of(1L, NamespaceRole.ADMIN), Set.of())).isNotNull();

        assertThatThrownBy(() -> service.get(
                "operation-1", "unrelated", Map.of(), Set.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.operation.notFound");
        verify(memberRepository, times(1)).findByOperationIdOrderByPosition("operation-1");
    }

    @Test
    void redactsPrivateMemberMetadataWhenOperationOwnerLostMemberAccess() {
        Namespace targetNamespace = mock(Namespace.class);
        when(targetNamespace.getSlug()).thenReturn("global");
        Namespace privateNamespace = mock(Namespace.class);
        when(privateNamespace.getSlug()).thenReturn("private-team");
        when(privateNamespace.getId()).thenReturn(2L);
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation()));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(targetNamespace));
        when(namespaceRepository.findBySlugIn(List.of("private-team")))
                .thenReturn(List.of(privateNamespace));
        when(memberRepository.findByOperationIdOrderByPosition("operation-1"))
                .thenReturn(List.of(member("private-team", SkillVisibility.PRIVATE)));
        Skill privateSkill = skill(10L, 2L, "different-owner", SkillVisibility.PRIVATE);
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(privateSkill));

        var response = service.get("operation-1", "actor", Map.of(), Set.of());

        assertThat(response.members()).singleElement().satisfies(member -> {
            assertThat(member.redacted()).isTrue();
            assertThat(member.status()).isNotNull();
            assertThat(member.coordinate()).isNull();
            assertThat(member.packagePath()).isNull();
            assertThat(member.skillId()).isNull();
            assertThat(member.version()).isNull();
            assertThat(member.errors()).isEmpty();
            assertThat(member.warnings()).isEmpty();
        });
    }

    @Test
    void delegatesActiveOperationPagingToTheReadModel() {
        PageResponse<SkillSuiteBundleOperationSummaryResponse> expected =
                new PageResponse<>(List.of(), 0, 2, 50);
        when(operationQueryRepository.findActive("actor", 2, 50)).thenReturn(expected);

        assertThat(service.listActive("actor", 2, 100)).isSameAs(expected);
        verify(operationQueryRepository).findActive("actor", 2, 50);
    }

    @Test
    void updateOperationIncludesItsBaseSuiteVersion() {
        SkillSuiteBundleExecutionOperation operation = new SkillSuiteBundleExecutionOperation(
                "operation-update", "preview-update", "request-update", "actor", SkillSuiteBundleMode.UPDATE,
                1L, "suite", 40L, 50L, "1.1.0", "temporary/archive.zip", "a".repeat(64),
                Map.of(), "warning-digest", NOW);
        Namespace namespace = mock(Namespace.class);
        when(namespace.getSlug()).thenReturn("global");
        SkillSuiteVersion baseVersion = mock(SkillSuiteVersion.class);
        when(baseVersion.getVersion()).thenReturn("1.0.0");
        when(operationRepository.findById("operation-update")).thenReturn(Optional.of(operation));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(memberRepository.findByOperationIdOrderByPosition("operation-update")).thenReturn(List.of());
        when(suiteVersionRepository.findById(50L)).thenReturn(Optional.of(baseVersion));

        var response = service.get("operation-update", "actor", Map.of(), Set.of());

        assertThat(response.baseVersion()).isEqualTo("1.0.0");
    }

    @Test
    void delegatesOperationHistoryPagingToTheReadModel() {
        SkillSuiteBundleOperationPageResponse expected =
                new SkillSuiteBundleOperationPageResponse(List.of(), 0, 2, 50, true);
        when(operationQueryRepository.findMine("actor", 2, 50)).thenReturn(expected);

        assertThat(service.listMine("actor", 2, 100)).isSameAs(expected);
        verify(operationQueryRepository).findMine("actor", 2, 50);
    }

    private SkillSuiteBundleExecutionOperation operation() {
        return new SkillSuiteBundleExecutionOperation(
                "operation-1", "preview-1", "request-1", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "temporary/archive.zip", "a".repeat(64),
                Map.of("secret", "internal-plan-value"), "warning-digest", NOW);
    }

    private SkillSuiteBundleMemberResult member() {
        return member("global", SkillVisibility.PUBLIC);
    }

    private SkillSuiteBundleMemberResult member(String namespace, SkillVisibility visibility) {
        return new SkillSuiteBundleMemberResult(
                "operation-1", 0, new SkillSuiteBundleCoordinate(namespace, "member"),
                SkillSuiteBundleMemberSourceType.PACKAGE, "members/member", visibility, "1.0.0",
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_VERSION,
                "sha256:member", 10L, 20L, List.of(), List.of("warning"), NOW);
    }

    private Skill skill(Long id, Long namespaceId, String ownerId, SkillVisibility visibility) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getNamespaceId()).thenReturn(namespaceId);
        when(skill.getOwnerId()).thenReturn(ownerId);
        when(skill.getVisibility()).thenReturn(visibility);
        when(skill.getLatestVersionId()).thenReturn(20L);
        return skill;
    }
}
