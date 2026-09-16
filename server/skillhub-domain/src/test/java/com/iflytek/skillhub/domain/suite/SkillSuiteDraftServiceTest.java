package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuiteDraftServiceTest {

    @Mock private SkillSuiteRepository suiteRepository;
    @Mock private SkillSuiteVersionRepository versionRepository;
    @Mock private SkillSuiteVersionMemberRepository memberRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillSuitePublicationValidator publicationValidator;
    @Mock private AuditLogService auditLogService;

    private SkillSuiteDraftService service;

    @BeforeEach
    void setUp() {
        service = new SkillSuiteDraftService(
                suiteRepository, versionRepository, memberRepository, namespaceRepository,
                publicationValidator, auditLogService);
    }

    @Test
    void createsOrderedExactMemberSnapshots() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "writers")).thenReturn(Optional.empty());
        when(suiteRepository.save(any())).thenAnswer(invocation -> {
            SkillSuite suite = invocation.getArgument(0);
            setId(suite, 10L);
            return suite;
        });
        when(versionRepository.save(any())).thenAnswer(invocation -> {
            SkillSuiteVersion version = invocation.getArgument(0);
            setId(version, 20L);
            return version;
        });
        when(memberRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillSuiteMemberSelection member = new SkillSuiteMemberSelection(
                30L, 40L, "global", "writer", "1.0.0", "sha256:abc");
        SkillSuiteMemberSelection supportingMember = new SkillSuiteMemberSelection(
                31L, 41L, "global", "editor", "1.0.0", "sha256:def");
        SkillSuiteDraftService.CreatedDraft result = service.create(
                new CreateSkillSuiteDraftCommand(
                        1L, "writers", "Writers", "Writing tools", "## Start here", "1.0.0",
                        SkillVisibility.PRIVATE, null, 40L, List.of(member, supportingMember)),
                new SkillSuiteActionContext(
                        "author", Map.of(1L, NamespaceRole.MEMBER), Set.of(),
                        "request-1", "127.0.0.1", "test"));

        assertThat(result.members()).filteredOn(SkillSuiteVersionMember::isEntry)
                .singleElement().satisfies(saved -> {
            assertThat(saved.getSuiteVersionId()).isEqualTo(20L);
            assertThat(saved.getSkillVersionId()).isEqualTo(40L);
            assertThat(saved.getPosition()).isZero();
            assertThat(saved.getFingerprintSnapshot()).isEqualTo("sha256:abc");
        });
        assertThat(result.members()).filteredOn(memberSnapshot -> !memberSnapshot.isEntry())
                .singleElement().satisfies(saved -> {
                    assertThat(saved.getSkillVersionId()).isEqualTo(41L);
                    assertThat(saved.getPosition()).isEqualTo(1);
                });
        assertThat(result.version().getOverview()).isEqualTo("## Start here");
        verify(publicationValidator).validate(result.suite(), result.version());
    }

    @Test
    void updatesOnlyAnEditableDraftAndReplacesItsExactMemberSnapshots() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        SkillSuite suite = new SkillSuite(1L, "writers", "Writers", "author");
        setId(suite, 10L);
        SkillSuiteVersion version = new SkillSuiteVersion(
                10L, "2.0.0", SkillVisibility.PUBLIC, "author");
        setId(version, 20L);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findByIdForDefinitionUpdate(20L)).thenReturn(Optional.of(version));
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillSuiteMemberSelection replacement = new SkillSuiteMemberSelection(
                31L, 41L, "global", "editor", "2.1.0", "sha256:def");
        SkillSuiteDraftService.CreatedDraft result = service.updateDraft(
                10L,
                20L,
                new CreateSkillSuiteDraftCommand(
                        1L, "writers", "Writing Suite", "Updated", "## Updated flow", "2.0.0",
                        SkillVisibility.PRIVATE, "Changed members", 41L, List.of(replacement)),
                new SkillSuiteActionContext(
                        "author", Map.of(1L, NamespaceRole.MEMBER), Set.of(),
                        "request-2", "127.0.0.1", "test"));

        assertThat(result.suite().getDisplayName()).isEqualTo("Writers");
        assertThat(result.version().getDisplayName()).isEqualTo("Writing Suite");
        assertThat(result.version().getSummary()).isEqualTo("Updated");
        assertThat(result.version().getOverview()).isEqualTo("## Updated flow");
        assertThat(result.version().getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(result.members()).singleElement().satisfies(saved -> {
            assertThat(saved.getSkillVersionId()).isEqualTo(41L);
            assertThat(saved.getPosition()).isZero();
        });
        verify(memberRepository).deleteBySuiteVersionId(20L);
        verify(publicationValidator).validate(suite, version);
    }

    @Test
    void rejectsUnsafeSuiteVersionsAtEveryDraftWriteEntryPoint() {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        SkillSuite suite = new SkillSuite(1L, "writers", "Writers", "author");
        setId(suite, 10L);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        SkillSuiteActionContext context = new SkillSuiteActionContext(
                "author", Map.of(1L, NamespaceRole.MEMBER), Set.of(),
                "request-3", "127.0.0.1", "test");

        for (String version : List.of("1.0.0; touch pwned", "a".repeat(65))) {
            SkillSuiteVersion existingVersion = new SkillSuiteVersion(
                    10L, version, SkillVisibility.PUBLIC, "author");
            setId(existingVersion, 20L);
            when(versionRepository.findByIdForDefinitionUpdate(20L))
                    .thenReturn(Optional.of(existingVersion));
            CreateSkillSuiteDraftCommand command = new CreateSkillSuiteDraftCommand(
                    1L, "writers", "Writers", "Summary", null, version,
                    SkillVisibility.PUBLIC, null, 40L,
                    List.of(new SkillSuiteMemberSelection(
                            30L, 40L, "global", "writer", "1.0.0", "sha256:abc")));

            assertThatThrownBy(() -> service.create(command, context))
                    .isInstanceOf(DomainBadRequestException.class)
                    .hasMessage("error.suite.version.invalid");
            assertThatThrownBy(() -> service.createVersion(10L, command, context))
                    .isInstanceOf(DomainBadRequestException.class)
                    .hasMessage("error.suite.version.invalid");
            assertThatThrownBy(() -> service.updateDraft(10L, 20L, command, context))
                    .isInstanceOf(DomainBadRequestException.class)
                    .hasMessage("error.suite.version.invalid");
        }
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
