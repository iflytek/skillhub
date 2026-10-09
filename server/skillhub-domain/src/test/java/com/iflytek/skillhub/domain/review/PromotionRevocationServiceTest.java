package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionRevocationServiceTest {
    @Mock PromotionRequestRepository promotionRepository;
    @Mock PromotionRevocationRequestRepository revocationRepository;
    @Mock PromotionRevocationHistoryRepository historyRepository;
    @Mock SkillRepository skillRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock SkillHardDeleteService hardDeleteService;

    private PromotionRevocationService service;
    private Skill source;
    private Skill target;
    private PromotionRequest initial;

    @BeforeEach
    void setUp() {
        service = new PromotionRevocationService(promotionRepository, revocationRepository,
                historyRepository, skillRepository, namespaceRepository, hardDeleteService,
                Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC));
        source = new Skill(2L, "foo", "owner-1", SkillVisibility.PUBLIC);
        target = new Skill(1L, "foo", "owner-1", SkillVisibility.PUBLIC);
        initial = new PromotionRequest(10L, 20L, 1L, "owner-1");
        setId(source, 10L);
        setId(target, 30L);
        target.setSourceSkillId(10L);
        setId(initial, 40L);
        initial.setTargetSkillId(30L);
        initial.setStatus(ReviewTaskStatus.APPROVED);
    }

    @Test
    void submitRejectsUnrelatedActorBeforeFindingTarget() {
        when(skillRepository.findById(10L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.submit(10L, "other", Map.of(), Set.of(), "reason"))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void approvalDetachesHistoryBeforeDeletingExactTarget() {
        PromotionRevocationRequest request = new PromotionRevocationRequest(
                40L, 10L, 30L, 2L, 1L, "foo", "owner-1", "withdraw");
        setId(request, 50L);
        when(revocationRepository.findById(50L)).thenReturn(Optional.of(request));
        when(historyRepository.lockTarget(30L)).thenReturn(true);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(source));
        when(skillRepository.findById(30L)).thenReturn(Optional.of(target));
        when(promotionRepository.findActiveInitialBySourceSkillId(10L)).thenReturn(Optional.of(initial));
        Namespace global = new Namespace("global", "Global", "admin-1");
        global.setType(NamespaceType.GLOBAL);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(global));
        when(historyRepository.detachTarget(30L, "admin-1", Instant.parse("2026-10-09T00:00:00Z")))
                .thenReturn(1);

        PromotionRevocationRequest approved = service.approve(50L, "admin-1",
                Set.of("SKILL_ADMIN"), "ok", null, null);

        assertThat(approved.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        InOrder order = inOrder(historyRepository, hardDeleteService);
        order.verify(historyRepository).lockTarget(30L);
        order.verify(historyRepository).detachTarget(30L, "admin-1", Instant.parse("2026-10-09T00:00:00Z"));
        order.verify(hardDeleteService).hardDeleteRevokedPromotionTarget(target, "global", "admin-1", null, null);
        verify(revocationRepository).save(request);
    }

    @Test
    void directRevocationApprovesExistingPendingRequestInsteadOfCreatingAnother() {
        PromotionRevocationRequest pending = new PromotionRevocationRequest(
                40L, 10L, 30L, 2L, 1L, "foo", "owner-1", "withdraw");
        setId(pending, 50L);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(source));
        when(skillRepository.findById(30L)).thenReturn(Optional.of(target));
        when(promotionRepository.findActiveInitialBySourceSkillId(10L)).thenReturn(Optional.of(initial));
        when(revocationRepository.findByTargetSkillIdAndStatus(30L, ReviewTaskStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(historyRepository.lockTarget(30L)).thenReturn(true);
        Namespace global = new Namespace("global", "Global", "admin-1");
        global.setType(NamespaceType.GLOBAL);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(global));
        when(historyRepository.detachTarget(30L, "admin-1", Instant.parse("2026-10-09T00:00:00Z")))
                .thenReturn(1);

        PromotionRevocationRequest result = service.revokeDirect(10L, "admin-1",
                Set.of("SKILL_ADMIN"), "urgent", null, null);

        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        verify(hardDeleteService).hardDeleteRevokedPromotionTarget(target, "global", "admin-1", null, null);
    }

    private static void setId(Object object, Long id) {
        try {
            var field = object.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(object, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
