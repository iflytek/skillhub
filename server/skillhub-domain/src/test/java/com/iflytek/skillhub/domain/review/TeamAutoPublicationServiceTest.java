package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamAutoPublicationServiceTest {

    private static final Long NAMESPACE_ID = 1L;
    private static final Long SKILL_ID = 7L;
    private static final Long VERSION_ID = 70L;
    private static final Long TASK_ID = 700L;
    private static final String MEMBER = "publisher";
    private static final String SCAN = "scan-task-1";

    @Mock private ReviewTaskRepository tasks;
    @Mock private NamespaceRepository namespaces;
    @Mock private NamespaceMemberRepository members;
    @Mock private SkillRepository skills;
    @Mock private SkillVersionRepository versions;
    @Mock private SecurityAuditRepository audits;
    @Mock private UserAccountRepository users;
    @Mock private ReviewAutoPublicationHandoff handoff;
    @Mock private ReviewService reviewService;
    @Mock private AuditLogService auditLogService;
    @Mock private EntityManager entityManager;

    private TeamAutoPublicationService service;
    private Namespace namespace;
    private NamespaceMember member;
    private Skill skill;
    private SkillVersion version;
    private ReviewTask task;
    private SecurityAudit audit;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        service = new TeamAutoPublicationService(tasks, namespaces, skills, versions, audits, users,
                new TeamAutoPublicationPolicy(members), handoff, reviewService, auditLogService, entityManager);
        namespace = new Namespace("team", "Team", "owner");
        setId(namespace, NAMESPACE_ID);
        member = new NamespaceMember(NAMESPACE_ID, MEMBER, NamespaceRole.MEMBER);
        UUID grant = member.grantTeamAutoPublication();
        skill = new Skill(NAMESPACE_ID, "sample", MEMBER, SkillVisibility.NAMESPACE_ONLY);
        setId(skill, SKILL_ID);
        version = new SkillVersion(SKILL_ID, "1.0.0", MEMBER);
        setId(version, VERSION_ID);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        version.setRequestedVisibility(SkillVisibility.NAMESPACE_ONLY);
        task = new ReviewTask(VERSION_ID, SKILL_ID, NAMESPACE_ID, "1.0.0", MEMBER);
        setId(task, TASK_ID);
        task.captureAutoPublicationEligibility(grant, true);
        audit = new SecurityAudit(VERSION_ID, ScannerType.SKILL_SCANNER, SCAN);
        audit.setVerdict(SecurityVerdict.SAFE);
        audit.setIsSafe(true);
        audit.setScannedAt(Instant.now());
        account = new UserAccount(MEMBER, MEMBER, null, null);

        when(tasks.findBySkillVersionIdAndStatus(VERSION_ID, ReviewTaskStatus.PENDING)).thenReturn(Optional.of(task));
        when(tasks.findBySkillIdAndStatus(SKILL_ID, ReviewTaskStatus.PENDING)).thenReturn(List.of(task));
        when(namespaces.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
        when(members.findByNamespaceIdAndUserId(NAMESPACE_ID, MEMBER)).thenReturn(Optional.of(member));
        when(users.findById(MEMBER)).thenReturn(Optional.of(account));
        when(skills.findById(SKILL_ID)).thenReturn(Optional.of(skill));
        when(skills.findByNamespaceIdAndSlug(NAMESPACE_ID, "sample")).thenReturn(List.of(skill));
        when(versions.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(audits.findLatestActiveByVersionIdAndScannerType(VERSION_ID, ScannerType.SKILL_SCANNER))
                .thenReturn(Optional.of(audit));
        when(reviewService.approveAutomatically(task, skill, version, SCAN)).thenReturn(true);
    }

    @Test
    void currentSafeRoundWithMatchingGrantApprovesAsSystemAndAudits() {
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService).approveAutomatically(task, skill, version, SCAN);
        verify(auditLogService).record(isNull(), eq("TEAM_AUTO_PUBLICATION_APPROVE"), eq("REVIEW_TASK"),
                eq(TASK_ID), isNull(), isNull(), isNull(), anyString());
        verify(handoff, never()).handOff(any());
    }

    @Test
    void nonSafeVerdictsFallBackToNormalReview() {
        for (SecurityVerdict verdict : List.of(SecurityVerdict.SUSPICIOUS, SecurityVerdict.DANGEROUS,
                SecurityVerdict.BLOCKED)) {
            service.onCurrentScanCompleted(VERSION_ID, SCAN, verdict);
        }

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
        verify(handoff, org.mockito.Mockito.times(3)).handOff(task);
    }

    @Test
    void scanFailureFallsBackWithoutApproval() {
        service.onCurrentScanFailed(VERSION_ID, SCAN);

        verify(handoff).handOff(task);
        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
    }

    @Test
    void revokedOrRegrantedGrantNeverRevivesTask() {
        member.revokeTeamAutoPublication();
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        member.grantTeamAutoPublication();
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
        verify(handoff, org.mockito.Mockito.times(2)).handOff(task);
    }

    @Test
    void cancelledEligibilityStaysManualEvenWithMatchingGrant() {
        task.cancelAutoPublication();

        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
    }

    @Test
    void staleOrUnsafeRecordedRoundIsNotApprovalBasis() {
        service.onCurrentScanCompleted(VERSION_ID, "older-scan", SecurityVerdict.SAFE);
        audit.setIsSafe(false);
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
    }

    @Test
    void governanceAndMembershipLossBlockApproval() throws Exception {
        namespace.setStatus(NamespaceStatus.FROZEN);
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        skill.setHidden(true);
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        skill.setHidden(false);

        skill.setVisibility(SkillVisibility.PRIVATE);
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        skill.setVisibility(SkillVisibility.NAMESPACE_ONLY);

        account.setStatus(UserStatus.DISABLED);
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        account.setStatus(UserStatus.ACTIVE);

        when(members.findByNamespaceIdAndUserId(NAMESPACE_ID, MEMBER)).thenReturn(Optional.empty());
        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
        verify(handoff, org.mockito.Mockito.times(5)).handOff(task);
    }

    @Test
    void newerPendingSubmissionSupersedesOlderTask() throws Exception {
        ReviewTask newer = new ReviewTask(71L, SKILL_ID, NAMESPACE_ID, "1.0.1", MEMBER);
        setId(newer, TASK_ID + 1);
        when(tasks.findBySkillIdAndStatus(SKILL_ID, ReviewTaskStatus.PENDING)).thenReturn(List.of(task, newer));

        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
    }

    @Test
    void ungrantedTaskIsIgnoredEntirely() {
        ReviewTask manual = new ReviewTask(VERSION_ID, SKILL_ID, NAMESPACE_ID, "1.0.0", MEMBER);
        when(tasks.findBySkillVersionIdAndStatus(VERSION_ID, ReviewTaskStatus.PENDING)).thenReturn(Optional.of(manual));

        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);
        service.onCurrentScanFailed(VERSION_ID, SCAN);

        verify(reviewService, never()).approveAutomatically(any(), any(), any(), any());
        verify(handoff, never()).handOff(any());
    }

    @Test
    void lostRaceToAnotherTransitionDoesNotAuditApproval() {
        when(reviewService.approveAutomatically(task, skill, version, SCAN)).thenReturn(false);

        service.onCurrentScanCompleted(VERSION_ID, SCAN, SecurityVerdict.SAFE);

        verify(auditLogService, never()).record(any(), eq("TEAM_AUTO_PUBLICATION_APPROVE"), any(), any(),
                any(), any(), any(), any());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
