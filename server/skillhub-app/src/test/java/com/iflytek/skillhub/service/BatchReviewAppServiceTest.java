package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.dto.BatchReviewRequest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BatchReviewAppServiceTest {
    ReviewPortalAppService portal = mock(ReviewPortalAppService.class);
    ReviewTaskRepository reviews = mock(ReviewTaskRepository.class);
    SkillRepository skills = mock(SkillRepository.class);
    SkillVersionRepository versions = mock(SkillVersionRepository.class);
    SecurityAuditRepository audits = mock(SecurityAuditRepository.class);
    NamespaceRepository namespaces = mock(NamespaceRepository.class);
    NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    RbacService rbac = mock(RbacService.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    EntityManager em = mock(EntityManager.class);
    BatchReviewAppService service;

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenAnswer(call -> mock(TransactionStatus.class));
        when(rbac.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"));
        Namespace ns = mock(Namespace.class);
        when(ns.getType()).thenReturn(NamespaceType.TEAM);
        when(ns.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(namespaces.findById(10L)).thenReturn(Optional.of(ns));
        service = new BatchReviewAppService(portal, reviews, skills, versions, audits,
                namespaces, members, new ReviewPermissionChecker(), rbac, em,
                new StaticMessageSource(), transactions);
    }

    void ready(long id) {
        ReviewTask task = mock(ReviewTask.class);
        when(task.getId()).thenReturn(id);
        when(task.getAvatarCandidateId()).thenReturn(null);
        when(task.getSubjectType()).thenReturn(ReviewSubjectType.SKILL_VERSION);
        when(task.getNamespaceId()).thenReturn(10L);
        when(task.getSkillId()).thenReturn(id + 100);
        when(task.getSkillVersionId()).thenReturn(id + 200);
        when(task.getSubmittedBy()).thenReturn("author");
        when(task.getStatus()).thenReturn(ReviewTaskStatus.PENDING);
        when(reviews.findById(id)).thenReturn(Optional.of(task));
        when(skills.findLockedById(id + 100)).thenReturn(Optional.of(mock(Skill.class)));
        SkillVersion version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(id + 200);
        when(version.getStatus()).thenReturn(SkillVersionStatus.PENDING_REVIEW);
        when(versions.findById(id + 200)).thenReturn(Optional.of(version));
        when(versions.findByIdForUpdate(id + 200)).thenReturn(Optional.of(version));
        SecurityAudit audit = new SecurityAudit(id + 200, ScannerType.SKILL_SCANNER, "scan-" + id);
        audit.setVerdict(SecurityVerdict.SAFE); audit.setIsSafe(true); audit.setScannedAt(Instant.now());
        when(audits.findLatestActiveByVersionIdAndScannerType(id + 200, ScannerType.SKILL_SCANNER)).thenReturn(Optional.of(audit));
    }
    BatchReviewRequest request(List<Long> ids) { return new BatchReviewRequest(ids, BatchReviewRequest.Action.APPROVE, "checked"); }

    @Test void previewChecksWithoutDispatchingDecisions() {
        ready(1); ready(2);
        when(audits.findLatestActiveByVersionIdAndScannerType(202L, ScannerType.SKILL_SCANNER)).thenReturn(Optional.empty());
        var result = service.process(request(List.of(1L, 2L)), "admin", null, true);
        assertThat(result.items()).extracting(item -> item.outcome()).containsExactly("READY", "SKIPPED");
        verifyNoInteractions(portal);
        verify(skills, never()).findLockedById(any());
    }
    @Test void oneFailureRollsBackOnlyThatItemAndNextItemStillCommits() {
        ready(1); ready(2);
        when(portal.approveReview(eq(1L), any(), eq("admin"), any(), any())).thenThrow(new DomainBadRequestException("conflict"));
        var result = service.process(request(List.of(1L, 2L)), "admin", null, false);
        assertThat(result.items()).extracting(item -> item.outcome()).containsExactly("SKIPPED", "APPROVED");
        verify(transactions).rollback(any()); verify(transactions).commit(any());
        verify(transactions, times(2)).getTransaction(argThat(def -> def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }
    @Test void authorizationIsReloadedAfterLocksAndBeforeEveryDecision() {
        ready(1); ready(2);
        when(rbac.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"), Set.of("SUPER_ADMIN"), Set.of(), Set.of());
        var result = service.process(request(List.of(1L, 2L)), "admin", null, false);
        assertThat(result.items()).extracting(item -> item.outcome()).containsExactly("APPROVED", "SKIPPED");
        verify(portal, never()).approveReview(eq(2L), any(), any(), any(), any());
    }
    @Test void cannotPublishStaleOrUnsafeScanOrUnsupportedReviewKind() {
        ready(1); ready(2);
        when(reviews.findById(2L).orElseThrow().getAvatarCandidateId()).thenReturn(99L);
        when(versions.findById(201L).orElseThrow().getStatus()).thenReturn(SkillVersionStatus.SCANNING);
        var result = service.process(request(List.of(1L, 2L)), "admin", null, false);
        assertThat(result.items()).extracting(item -> item.reason()).containsExactly("review.batch.scanRequired", "review.batch.unsupported");
        verifyNoInteractions(portal);
    }
    @Test void duplicateAndOversizedRequestsAreRejectedBeforeAnyWork() {
        assertThatThrownBy(() -> service.process(request(List.of(1L,1L)), "admin", null, false)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.process(request(LongStream.rangeClosed(1,51).boxed().toList()), "admin", null, false)).isInstanceOf(DomainBadRequestException.class);
        verifyNoInteractions(reviews, portal, transactions);
    }
    @Test void rejectionRequiresReasonAndDoesNotRequireSafeScan() {
        ready(1);
        assertThatThrownBy(() -> service.process(new BatchReviewRequest(List.of(1L), BatchReviewRequest.Action.REJECT, " "), "admin", null, false)).isInstanceOf(DomainBadRequestException.class);
        var result = service.process(new BatchReviewRequest(List.of(1L), BatchReviewRequest.Action.REJECT, " fix "), "admin", null, false);
        assertThat(result.items().getFirst().outcome()).isEqualTo("REJECTED");
        verify(portal).rejectReview(1L,"fix","admin",Map.of(),null);
        verifyNoInteractions(audits);
    }
    @Test void normalMemberCannotUseBatchToGainReviewPermission() {
        ready(1); when(rbac.getUserRoleCodes("admin")).thenReturn(Set.of());
        when(members.findByNamespaceIdAndUserId(10L,"admin")).thenReturn(Optional.of(new NamespaceMember(10L,"admin",NamespaceRole.MEMBER)));
        assertThat(service.process(request(List.of(1L)),"admin",null,false).items().getFirst().reason()).isEqualTo("review.no_permission");
        verifyNoInteractions(portal);
        verify(skills,never()).findLockedById(any());
    }
}
