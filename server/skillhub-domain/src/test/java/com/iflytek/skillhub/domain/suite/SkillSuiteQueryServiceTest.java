package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuiteQueryServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillSuiteRepository suiteRepository;
    @Mock private SkillSuiteVersionRepository versionRepository;
    @Mock private SkillSuiteVersionMemberRepository memberRepository;
    @Mock private SkillSuiteMemberStateResolver stateResolver;
    @Mock private ReviewTaskRepository reviewTaskRepository;

    private SkillSuiteQueryService service;

    @BeforeEach
    void setUp() {
        service = new SkillSuiteQueryService(
                namespaceRepository, suiteRepository, versionRepository, memberRepository, stateResolver,
                reviewTaskRepository, new ReviewPermissionChecker());
    }

    @Test
    void anonymousUserReadsPublishedPublicSuiteWithOrderedAvailability() {
        Namespace namespace = new Namespace("global", "Global", "system");
        SkillSuite suite = new SkillSuite(1L, "writers", "Writers", "author");
        SkillSuiteVersion version = new SkillSuiteVersion(10L, "1.0.0", SkillVisibility.PUBLIC, "author");
        SkillSuiteVersionMember member = new SkillSuiteVersionMember(
                20L,
                new SkillSuiteMemberSelection(30L, 40L, "global", "writer", "2.0.0", "sha256:abc"),
                0,
                true);
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.parse("2026-09-07T08:00:00Z"));
        suite.setLatestVersionId(20L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "writers")).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of(member));
        when(stateResolver.resolveForViewer(List.of(member), null, Map.of(), Set.of()))
                .thenReturn(List.of(new SkillSuiteMemberState(
                30L, 40L, 1L, "Writer", "Writing helper",
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, false, true)));

        SkillSuiteQueryService.Detail result = service.getDetail(
                "global", "writers", null, null, Map.of(), Set.of());

        assertThat(result.available()).isTrue();
        assertThat(result.version().getVersion()).isEqualTo("1.0.0");
        assertThat(result.members()).singleElement().satisfies(item -> {
            assertThat(item.snapshot().getSkillVersionId()).isEqualTo(40L);
            assertThat(item.availability().available()).isTrue();
        });
    }

    @Test
    void privatePublishedSuiteUsesTheSuiteCreatorInsteadOfTheVersionAuthor() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        SkillSuite suite = new SkillSuite(1L, "private-suite", "Private Suite", "suite-author");
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "2.0.0", SkillVisibility.PRIVATE, "delegated-version-author");
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suite.setLatestVersionId(20L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "private-suite")).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of());

        SkillSuiteQueryService.Detail creatorView = service.getDetail(
                "team", "private-suite", null, "suite-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(creatorView.version().getVersion()).isEqualTo("2.0.0");
        assertThatThrownBy(() -> service.getDetail(
                "team", "private-suite", null, "delegated-version-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void unpublishedSuiteUsesTheSuiteCreatorInsteadOfTheVersionAuthor() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        SkillSuite suite = new SkillSuite(1L, "draft-suite", "Draft Suite", "suite-author");
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "2.0.0", SkillVisibility.PUBLIC, "delegated-version-author");
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "draft-suite")).thenReturn(Optional.of(suite));
        when(versionRepository.findBySuiteIdAndVersion(10L, "2.0.0")).thenReturn(Optional.of(version));
        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of());

        for (SkillSuiteVersionStatus status : List.of(
                SkillSuiteVersionStatus.DRAFT, SkillSuiteVersionStatus.REJECTED)) {
            version.setStatus(status);
            assertThat(service.getDetail(
                    "team", "draft-suite", "2.0.0", "suite-author",
                    Map.of(1L, NamespaceRole.MEMBER), Set.of()).version().getStatus())
                    .isEqualTo(status);
            assertThatThrownBy(() -> service.getDetail(
                    "team", "draft-suite", "2.0.0", "delegated-version-author",
                    Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                    .isInstanceOf(DomainForbiddenException.class);
        }
    }

    @Test
    void suiteCreatorCanInspectHiddenAndArchivedPublishedSnapshots() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        SkillSuite suite = new SkillSuite(1L, "restricted-suite", "Restricted Suite", "suite-author");
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "1.0.0", SkillVisibility.PUBLIC, "delegated-version-author");
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suite.setLatestVersionId(20L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "restricted-suite")).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of());

        suite.setHidden(true);
        assertThat(service.getDetail(
                "team", "restricted-suite", null, "suite-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()).suite().isHidden()).isTrue();
        assertThatThrownBy(() -> service.getDetail(
                "team", "restricted-suite", null, "delegated-version-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);

        suite.setHidden(false);
        suite.setStatus(SkillSuiteStatus.ARCHIVED);
        assertThat(service.getDetail(
                "team", "restricted-suite", null, "suite-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()).suite().getStatus())
                .isEqualTo(SkillSuiteStatus.ARCHIVED);
        assertThatThrownBy(() -> service.getDetail(
                "team", "restricted-suite", null, "delegated-version-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void archivedNamespaceHidesPublishedPublicSuiteFromAnonymousUsers() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        SkillSuite suite = new SkillSuite(1L, "public-suite", "Public Suite", "suite-author");
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "1.0.0", SkillVisibility.PUBLIC, "suite-author");
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suite.setLatestVersionId(20L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "public-suite")).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.getDetail(
                "team", "public-suite", null, null, Map.of(), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);

        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of());
        assertThat(service.getDetail(
                "team", "public-suite", null, "member",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()).version().getVersion())
                .isEqualTo("1.0.0");
    }

    @Test
    void pendingReviewUsesTheActualSubmitterAndReviewPermissions() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        SkillSuite suite = new SkillSuite(1L, "pending-suite", "Pending Suite", "suite-author");
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "1.0.0", SkillVisibility.PUBLIC, "delegated-version-author");
        setId(namespace, 1L);
        setId(suite, 10L);
        setId(version, 20L);
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        ReviewTask task = ReviewTask.forSuiteVersion(
                20L, 10L, 1L, "1.0.0", "actual-submitter");

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "pending-suite")).thenReturn(Optional.of(suite));
        when(versionRepository.findBySuiteIdAndVersion(10L, "1.0.0")).thenReturn(Optional.of(version));
        when(reviewTaskRepository.findBySubjectTypeAndSubjectVersionIdAndStatus(
                ReviewSubjectType.SUITE_VERSION, 20L, ReviewTaskStatus.PENDING))
                .thenReturn(Optional.of(task));
        when(memberRepository.findBySuiteVersionIdOrderByPosition(20L)).thenReturn(List.of());

        assertThat(service.getDetail(
                "team", "pending-suite", "1.0.0", "actual-submitter",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()).version().getStatus())
                .isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
        assertThat(service.getDetail(
                "team", "pending-suite", "1.0.0", "owner",
                Map.of(1L, NamespaceRole.OWNER), Set.of()).version().getStatus())
                .isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
        assertThatThrownBy(() -> service.getDetail(
                "team", "pending-suite", "1.0.0", "suite-author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    private void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
