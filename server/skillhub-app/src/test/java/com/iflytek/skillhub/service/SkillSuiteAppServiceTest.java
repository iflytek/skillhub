package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.SkillSuiteCreateRequest;
import com.iflytek.skillhub.dto.SkillSuiteMemberRequest;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteAllowedAction;
import com.iflytek.skillhub.domain.suite.SkillSuiteDraftService;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallMetricsService;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperation;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperationRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteLifecycleService;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberAvailability;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberState;
import com.iflytek.skillhub.domain.suite.SkillSuiteQueryService;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.repository.SkillSuiteCandidateQueryRepository;
import com.iflytek.skillhub.repository.MySkillSuiteQueryRepository;
import com.iflytek.skillhub.repository.SkillSuiteReferenceQueryRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillSuiteAppServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillQueryService skillQueryService;
    @Mock private SkillSuiteDraftService draftService;
    @Mock private SkillSuiteLifecycleService lifecycleService;
    @Mock private SkillSuiteQueryService queryService;
    @Mock private SkillSuiteInstallMetricsService installMetricsService;
    @Mock private SkillSuiteInstallOperationRepository installOperationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private RequestIdAccessor requestIdAccessor;
    @Mock private SkillSuiteCandidateQueryRepository candidateQueryRepository;
    @Mock private MySkillSuiteQueryRepository mySkillSuiteQueryRepository;
    @Mock private SkillSuiteReferenceQueryRepository referenceQueryRepository;
    @Mock private HttpServletRequest request;
    private SkillSuiteAppService service;
    private Namespace namespace;
    private SkillSuite suite;
    private SkillSuiteVersion version;
    private SkillSuiteVersionMember firstMember;
    private SkillSuiteVersionMember secondMember;

    @BeforeEach
    void setUp() {
        service = new SkillSuiteAppService(
                namespaceRepository, skillQueryService, draftService, lifecycleService,
                queryService, installMetricsService, installOperationRepository, auditLogService,
                requestIdAccessor,
                candidateQueryRepository, mySkillSuiteQueryRepository, referenceQueryRepository);
        namespace = new Namespace("global", "Global", "admin");
        setField(namespace, "id", 1L);
        suite = new SkillSuite(1L, "starter", "Starter", "user-1");
        setField(suite, "id", 7L);
        version = new SkillSuiteVersion(7L, "1.0.0", SkillVisibility.PUBLIC, "user-1");
        setField(version, "id", 70L);
        version.setOverview("## Install in order");
        firstMember = member(11L, 101L, "first", "1.0.0", "sha256:first", 0);
        secondMember = member(12L, 102L, "second", "2.0.0", "sha256:second", 1);
    }

    @Test
    void createInstallPlan_resolvesEveryExactMemberBeforeRecordingMetrics() {
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail("global", "starter", null, "user-1", Map.of(), Set.of()))
                .willReturn(detail);
        given(skillQueryService.resolveVersionById(101L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(102L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(12L, 102L, "second", "2.0.0", "sha256:second"));
        given(installOperationRepository.insertIfAbsent(
                any(), org.mockito.ArgumentMatchers.eq("retry-1"),
                org.mockito.ArgumentMatchers.eq("user:user-1"),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(70L))).willReturn(1);

        var result = service.createInstallPlan(
                "global", "starter", null, "user-1", Map.of(), Set.of(), "retry-1", request);

        assertThat(result.operationId()).isNotBlank().isNotEqualTo("retry-1");
        assertThat(result.fingerprint()).startsWith("sha256:");
        assertThat(result.members()).extracting(member -> member.slug())
                .containsExactly("first", "second");
        assertThat(result.members()).extracting(member -> member.entry())
                .containsExactly(true, false);
        verify(installMetricsService).recordIssuedPlan(7L);
        verify(auditLogService, times(3)).record(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createInstallPlan_preservesSuperAdminAccessWhenResolvingPrivateMembers() {
        Set<String> platformRoles = Set.of("SUPER_ADMIN");
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail(
                "global", "starter", null, "super-admin", Map.of(), platformRoles))
                .willReturn(detail);
        given(skillQueryService.resolveVersionById(
                101L, "super-admin", Map.of(), platformRoles))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(
                102L, "super-admin", Map.of(), platformRoles))
                .willReturn(resolved(12L, 102L, "second", "2.0.0", "sha256:second"));
        given(installOperationRepository.insertIfAbsent(
                any(), org.mockito.ArgumentMatchers.eq("retry-super-admin"),
                org.mockito.ArgumentMatchers.eq("user:super-admin"),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(70L))).willReturn(1);

        var result = service.createInstallPlan(
                "global", "starter", null, "super-admin", Map.of(), platformRoles,
                "retry-super-admin", request);

        assertThat(result.members()).hasSize(2);
    }

    @Test
    void createInstallPlan_scopesAnonymousIdempotencyByCallerAndSuite() {
        HttpServletRequest secondRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn("192.0.2.10");
        given(request.getHeader("User-Agent")).willReturn("skillhub-cli/test");
        given(secondRequest.getRemoteAddr()).willReturn("192.0.2.11");
        given(secondRequest.getHeader("User-Agent")).willReturn("skillhub-cli/test");
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail("global", "starter", null, null, Map.of(), Set.of()))
                .willReturn(detail);
        given(skillQueryService.resolveVersionById(101L, null, Map.of(), Set.of()))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(102L, null, Map.of(), Set.of()))
                .willReturn(resolved(12L, 102L, "second", "2.0.0", "sha256:second"));
        given(installOperationRepository.insertIfAbsent(
                any(), org.mockito.ArgumentMatchers.eq("retry-anonymous"), any(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(70L))).willReturn(1);

        service.createInstallPlan(
                "global", "starter", null, null, Map.of(), Set.of(), "retry-anonymous", request);
        service.createInstallPlan(
                "global", "starter", null, null, Map.of(), Set.of(), "retry-anonymous", secondRequest);

        ArgumentCaptor<String> actorKeys = ArgumentCaptor.forClass(String.class);
        verify(installOperationRepository, times(2)).insertIfAbsent(
                any(), org.mockito.ArgumentMatchers.eq("retry-anonymous"), actorKeys.capture(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(70L));
        assertThat(actorKeys.getAllValues())
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allMatch(value -> value.startsWith("anonymous:"));
    }

    @Test
    void createInstallPlan_replaysTheCapturedVersionWithoutDuplicateMetrics() {
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetailByVersionId(
                "global", "starter", 70L, "user-1", Map.of(), Set.of())).willReturn(detail);
        given(skillQueryService.resolveVersionById(101L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(102L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(12L, 102L, "second", "2.0.0", "sha256:second"));
        SkillSuiteInstallOperation operation = org.mockito.Mockito.mock(SkillSuiteInstallOperation.class);
        given(operation.getOperationId()).willReturn("server-operation-1");
        given(operation.getSuiteId()).willReturn(7L);
        given(operation.getSuiteVersionId()).willReturn(70L);
        given(installOperationRepository.findByClientRequestIdAndActorKey("retry-1", "user:user-1"))
                .willReturn(java.util.Optional.of(operation));

        var result = service.createInstallPlan(
                "global", "starter", null, "user-1", Map.of(), Set.of(), "retry-1", request);

        assertThat(result.operationId()).isEqualTo("server-operation-1");
        assertThat(result.version()).isEqualTo("1.0.0");
        verify(installOperationRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
        verify(installMetricsService, never()).recordIssuedPlan(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createInstallPlan_replaysTheConcurrentWinnerWithoutDuplicateMetrics() {
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail("global", "starter", null, "user-1", Map.of(), Set.of()))
                .willReturn(detail);
        given(queryService.getDetailByVersionId(
                "global", "starter", 70L, "user-1", Map.of(), Set.of())).willReturn(detail);
        given(skillQueryService.resolveVersionById(101L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(102L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(12L, 102L, "second", "2.0.0", "sha256:second"));
        given(installOperationRepository.insertIfAbsent(
                any(), org.mockito.ArgumentMatchers.eq("retry-race"),
                org.mockito.ArgumentMatchers.eq("user:user-1"),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(70L))).willReturn(0);
        SkillSuiteInstallOperation operation = org.mockito.Mockito.mock(SkillSuiteInstallOperation.class);
        given(operation.getOperationId()).willReturn("concurrent-operation");
        given(operation.getSuiteId()).willReturn(7L);
        given(operation.getSuiteVersionId()).willReturn(70L);
        given(installOperationRepository.findByClientRequestIdAndActorKey("retry-race", "user:user-1"))
                .willReturn(java.util.Optional.empty(), java.util.Optional.of(operation));

        var result = service.createInstallPlan(
                "global", "starter", null, "user-1", Map.of(), Set.of(), "retry-race", request);

        assertThat(result.operationId()).isEqualTo("concurrent-operation");
        verify(installMetricsService, never()).recordIssuedPlan(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createInstallPlan_whenAnyMemberCannotBeResolved_recordsNothingAndHidesMemberDetails() {
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail("global", "starter", null, "user-1", Map.of(), Set.of()))
                .willReturn(detail);
        given(skillQueryService.resolveVersionById(101L, "user-1", Map.of(), Set.of()))
                .willReturn(resolved(11L, 101L, "first", "1.0.0", "sha256:first"));
        given(skillQueryService.resolveVersionById(102L, "user-1", Map.of(), Set.of()))
                .willThrow(new DomainForbiddenException("error.skill.access.denied", "private-skill"));

        assertThatThrownBy(() -> service.createInstallPlan(
                "global", "starter", null, "user-1", Map.of(), Set.of(), "retry-2", request))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageNotContaining("private-skill");

        verify(installMetricsService, never()).recordIssuedPlan(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createInstallPlan_rejectsDegradedSuiteBeforeResolvingMembers() {
        SkillSuiteQueryService.Detail detail = detail(false);
        given(queryService.getDetail("global", "starter", null, null, Map.of(), Set.of()))
                .willReturn(detail);

        assertThatThrownBy(() -> service.createInstallPlan(
                "global", "starter", null, null, Map.of(), Set.of(), "retry-3", request))
                .isInstanceOf(DomainBadRequestException.class);

        verify(skillQueryService, never()).resolveVersionById(any(), any(), any(), any());
        verify(installMetricsService, never()).recordIssuedPlan(any());
    }

    @Test
    void createInstallPlan_rejectsAnInvalidIdempotencyKey() {
        assertThatThrownBy(() -> service.createInstallPlan(
                "global", "starter", null, "user-1", Map.of(), Set.of(), "not allowed!", request))
                .isInstanceOf(DomainBadRequestException.class);

        verify(installOperationRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
        verify(installMetricsService, never()).recordIssuedPlan(any());
    }

    @Test
    void getDetail_exposesServerDerivedAllowedActions() {
        SkillSuiteQueryService.Detail detail = detail(true);
        given(queryService.getDetail(
                "global", "starter", null, "user-1", Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .willReturn(detail);
        given(lifecycleService.allowedActions(
                org.mockito.ArgumentMatchers.eq(suite), org.mockito.ArgumentMatchers.eq(version),
                org.mockito.ArgumentMatchers.eq(namespace), any()))
                .willReturn(Set.of(SkillSuiteAllowedAction.EDIT, SkillSuiteAllowedAction.CREATE_VERSION));

        var result = service.getDetail(
                "global", "starter", null, "user-1", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.allowedActions())
                .containsExactlyInAnyOrder(SkillSuiteAllowedAction.EDIT, SkillSuiteAllowedAction.CREATE_VERSION);
        assertThat(result.suiteStatus()).isEqualTo("ACTIVE");
        assertThat(result.hidden()).isFalse();
        assertThat(result.overview()).isEqualTo("## Install in order");
        assertThat(result.members()).extracting(member -> member.displayName())
                .containsExactly("First Skill", "Second Skill");
    }

    @Test
    void getDetail_hidesLiveMemberMetadataWhenTheViewerCannotReadThatSkill() {
        SkillSuiteMemberState restricted = new SkillSuiteMemberState(
                11L, 101L, 1L, "Private Skill", "Private summary", NamespaceStatus.ACTIVE,
                SkillVisibility.PRIVATE, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, false, false);
        SkillSuiteQueryService.Detail detail = new SkillSuiteQueryService.Detail(
                namespace, suite, version, true,
                List.of(new SkillSuiteQueryService.MemberDetail(
                        firstMember, restricted, SkillSuiteMemberAvailability.availableMember())));
        given(queryService.getDetail(
                "global", "starter", null, "suite-author", Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .willReturn(detail);

        var result = service.getDetail(
                "global", "starter", null, "suite-author", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.members()).singleElement().satisfies(member -> {
            assertThat(member.displayName()).isNull();
            assertThat(member.summary()).isNull();
            assertThat(member.browsable()).isFalse();
        });
    }

    @Test
    void create_rejectsCoordinateMismatchAfterResolvingWithPlatformRoles() {
        SkillSuiteMemberRequest member = new SkillSuiteMemberRequest(
                101L, "global", "selected", "1.0.0");
        SkillSuiteCreateRequest createRequest = new SkillSuiteCreateRequest(
                "global", "starter", "Starter", null, null, "1.0.0",
                SkillVisibility.PRIVATE, null, member, List.of(member));
        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillQueryService.resolveVersionById(
                101L, "user-1", Map.of(), Set.of("SUPER_ADMIN")))
                .willReturn(resolved(99L, 101L, "different", "1.0.0", "sha256:different"));

        assertThatThrownBy(() -> service.create(
                createRequest, "user-1", Map.of(), Set.of("SUPER_ADMIN"), request))
                .isInstanceOfSatisfying(DomainBadRequestException.class, exception ->
                        assertThat(exception.messageArgs()[0].toString())
                                .contains("@global/selected@1.0.0")
                                .contains("error.suite.members.selectionMismatch"));

        verify(draftService, never()).create(any(), any());
    }

    @Test
    void create_reportsEveryInvalidMemberCoordinateAndReason() {
        SkillSuiteMemberRequest first = new SkillSuiteMemberRequest(
                101L, "global", "missing", "1.0.0");
        SkillSuiteMemberRequest second = new SkillSuiteMemberRequest(
                102L, "private-team", "restricted", "2.0.0");
        SkillSuiteCreateRequest createRequest = new SkillSuiteCreateRequest(
                "global", "starter", "Starter", null, null, "1.0.0",
                SkillVisibility.PRIVATE, null, first, List.of(first, second));
        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillQueryService.resolveVersionById(101L, "user-1", Map.of(), Set.of()))
                .willThrow(new DomainBadRequestException("error.skill.version.notFound", 101L));
        given(skillQueryService.resolveVersionById(102L, "user-1", Map.of(), Set.of()))
                .willThrow(new DomainForbiddenException("error.skill.access.denied", "restricted"));

        DomainBadRequestException exception = catchThrowableOfType(
                () -> service.create(createRequest, "user-1", Map.of(), Set.of(), request),
                DomainBadRequestException.class);

        assertThat(exception.messageCode()).isEqualTo("error.suite.members.invalid");
        assertThat((String) exception.messageArgs()[0])
                .contains("@global/missing@1.0.0 (error.skill.version.notFound)")
                .contains("@private-team/restricted@2.0.0 (error.skill.access.denied)");
        verify(draftService, never()).create(any(), any());
    }

    private SkillSuiteQueryService.Detail detail(boolean available) {
        if (!available) {
            return new SkillSuiteQueryService.Detail(namespace, suite, version, false, List.of());
        }
        var first = new SkillSuiteQueryService.MemberDetail(
                firstMember, memberState(11L, 101L, "First Skill", "First summary"),
                SkillSuiteMemberAvailability.availableMember());
        var second = new SkillSuiteQueryService.MemberDetail(
                secondMember, memberState(12L, 102L, "Second Skill", "Second summary"),
                SkillSuiteMemberAvailability.availableMember());
        return new SkillSuiteQueryService.Detail(namespace, suite, version, true, List.of(first, second));
    }

    private SkillSuiteMemberState memberState(
            Long skillId,
            Long versionId,
            String displayName,
            String summary
    ) {
        return new SkillSuiteMemberState(
                skillId, versionId, 1L, displayName, summary, NamespaceStatus.ACTIVE,
                SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, false, true);
    }

    private SkillSuiteVersionMember member(
            Long skillId,
            Long versionId,
            String slug,
            String memberVersion,
            String fingerprint,
            int position
    ) {
        return new SkillSuiteVersionMember(
                70L,
                new com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection(
                        skillId, versionId, "global", slug, memberVersion, fingerprint),
                position,
                position == 0);
    }

    private SkillQueryService.ResolvedVersionDTO resolved(
            Long skillId,
            Long versionId,
            String slug,
            String version,
            String fingerprint
    ) {
        return new SkillQueryService.ResolvedVersionDTO(
                skillId, "global", slug, version, versionId, fingerprint, true,
                "/api/v1/skills/global/" + slug + "/versions/" + version + "/download");
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
