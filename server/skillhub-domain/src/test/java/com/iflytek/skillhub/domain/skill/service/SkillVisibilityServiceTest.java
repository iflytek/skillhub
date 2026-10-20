package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.event.SkillVisibilityChangedEvent;
import com.iflytek.skillhub.domain.security.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillVisibilityServiceTest {
    private final SkillRepository skills = mock(SkillRepository.class);
    private final SkillVersionRepository versions = mock(SkillVersionRepository.class);
    private final ReviewTaskRepository reviews = mock(ReviewTaskRepository.class);
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SecurityScanService scanner = mock(SecurityScanService.class);
    private final SecurityAuditRepository audits = mock(SecurityAuditRepository.class);
    private final SkillVisibilityService service = new SkillVisibilityService(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class), skills, versions, reviews, namespaces, events, scanner, audits);
    private Skill skill;

    @BeforeEach
    void setup() throws Exception {
        skill = new Skill(2L, "foo", "owner", SkillVisibility.PUBLIC);
        var id = Skill.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(skill, 1L);
        skill.setLatestVersionId(3L);
        when(skills.findLockedById(1L)).thenReturn(Optional.of(skill));
        Namespace ns = mock(Namespace.class);
        when(ns.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(namespaces.findById(2L)).thenReturn(Optional.of(ns));
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        when(versions.findById(3L)).thenReturn(Optional.of(version));
        SecurityAudit audit = new SecurityAudit(3L, ScannerType.SKILL_SCANNER, "scan-1");
        audit.setScannedAt(java.time.Instant.now());
        audit.setIsSafe(true);
        when(audits.findLatestActiveByVersionIdAndScannerType(3L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.of(audit));
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void narrowingPreservesPublishedVersionAndAppliesImmediately() {
        assertThat(service.change(1L, SkillVisibility.PRIVATE, "owner", Map.of(), Set.of())).isNull();
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(skill.getLatestVersionId()).isEqualTo(3L);
        verify(versions, never()).save(any());
        verify(reviews, never()).save(any());
        verify(events).publishEvent(any(SkillVisibilityChangedEvent.class));
    }

    @Test
    void expansionKeepsOldScopeUntilApproved() {
        skill.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        var task = service.change(1L, SkillVisibility.PUBLIC, "owner", Map.of(), Set.of());
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.NAMESPACE_ONLY);
        assertThat(task.getRequestedVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(task.getVisibilityVersionId()).isEqualTo(3L);
        assertThat(task.getSkillVersionId()).isNull();
        service.approve(task, "admin");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        verify(versions, never()).save(any());
    }

    @Test
    void pendingScopeBlocksSecondChange() {
        when(reviews.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(1L, ReviewTaskStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.change(1L, SkillVisibility.PRIVATE, "owner", Map.of(), Set.of()))
                .hasMessageContaining("error.skill.visibility.pending");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
    }

    @Test
    void pendingAvatarBlocksScopeChangeWithoutSupersedingIt() {
        skill.setPendingAvatarId(99L);
        assertThatThrownBy(() -> service.change(1L, SkillVisibility.PRIVATE, "owner", Map.of(), Set.of()))
                .hasMessageContaining("error.skill.visibility.avatarPending");
        assertThat(skill.getPendingAvatarId()).isEqualTo(99L);
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        verify(reviews, never()).save(any());
    }

    @Test
    void pendingVersionBlocksChange() {
        when(versions.findBySkillIdAndStatus(1L, SkillVersionStatus.PENDING_REVIEW))
                .thenReturn(List.of(new SkillVersion(1L, "2.0.0", "owner")));
        assertThatThrownBy(() -> service.change(1L, SkillVisibility.PRIVATE, "owner", Map.of(), Set.of()))
                .hasMessageContaining("error.skill.visibility.pending");
    }

    @Test
    void staleApprovalCannotOverwriteNewPublishedVersion() {
        skill.setVisibility(SkillVisibility.PRIVATE);
        var task = ReviewTask.forVisibility(skill, SkillVisibility.PUBLIC, "owner");
        task.setVisibilityScanTaskId("scan-1");
        skill.setLatestVersionId(4L);
        SkillVersion newer = new SkillVersion(1L, "2.0.0", "owner");
        newer.setStatus(SkillVersionStatus.PUBLISHED);
        when(versions.findById(4L)).thenReturn(Optional.of(newer));
        assertThatThrownBy(() -> service.approve(task, "admin")).hasMessageContaining("error.skill.visibility.stale");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    @Test
    void privateExpansionStartsScanAndCannotApproveUntilItsResultPasses() {
        skill.setVisibility(SkillVisibility.PRIVATE);
        when(audits.findLatestActiveByVersionIdAndScannerType(3L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.empty());
        when(scanner.isEnabled()).thenReturn(true);
        versions.findById(3L).orElseThrow().setBundleReady(true);
        when(scanner.scanPublishedVersion(any(), eq("owner"))).thenReturn(new ScanTask("new-scan", 3L, null,
                "packages/1/3/bundle.zip", "owner", 0L, Map.of()));
        var task = service.change(1L, SkillVisibility.PUBLIC, "owner", Map.of(), Set.of());
        assertThat(task.getVisibilityScanTaskId()).isEqualTo("new-scan");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThatThrownBy(() -> service.approve(task, "admin")).hasMessageContaining("error.skill.visibility.scanIncomplete");
        var old = new SecurityAudit(3L, ScannerType.SKILL_SCANNER, "old-scan");
        old.setScannedAt(java.time.Instant.now()); old.setIsSafe(true);
        when(audits.findLatestActiveByVersionIdAndScannerType(3L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.approve(task, "admin")).hasMessageContaining("error.skill.visibility.scanIncomplete");
        var current = new SecurityAudit(3L, ScannerType.SKILL_SCANNER, "new-scan");
        when(audits.findLatestActiveByVersionIdAndScannerType(3L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.approve(task, "admin")).hasMessageContaining("error.skill.visibility.scanIncomplete");
        current.markFailed(java.time.Instant.now(), "scanner unavailable");
        assertThatThrownBy(() -> service.approve(task, "admin")).hasMessageContaining("error.skill.visibility.scanIncomplete");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    @Test
    void missingScanWithDisabledScannerLeavesVisibilityUnchanged() {
        skill.setVisibility(SkillVisibility.PRIVATE);
        when(audits.findLatestActiveByVersionIdAndScannerType(3L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.change(1L, SkillVisibility.PUBLIC, "owner", Map.of(), Set.of()))
                .hasMessageContaining("error.security.scanner.required");
        verify(reviews, never()).save(any());
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    @Test
    void ordinaryMemberCannotManageOthersSkill() {
        assertThatThrownBy(() -> service.change(1L, SkillVisibility.PRIVATE, "stranger", Map.of(2L, NamespaceRole.MEMBER), Set.of()))
                .hasMessageContaining("error.skill.lifecycle.noPermission");
    }
}
