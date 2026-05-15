package com.iflytek.skillhub.domain.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PromotionCampaignService}. Covers slot lookup, target gating,
 * capacity rules, and the approval / rejection state machine including optimistic-lock loss.
 */
@ExtendWith(MockitoExtension.class)
class PromotionCampaignServiceTest {

    private PromotionSlotRepository slotRepository;
    private PromotionCampaignRepository campaignRepository;
    private PromotionEventLogRepository eventLogRepository;
    private PromotionTargetGuard targetGuard;
    private PromotionCampaignService service;

    private final Instant now = Instant.parse("2026-06-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        slotRepository = mock(PromotionSlotRepository.class);
        campaignRepository = mock(PromotionCampaignRepository.class);
        eventLogRepository = mock(PromotionEventLogRepository.class);
        targetGuard = mock(PromotionTargetGuard.class);
        service = new PromotionCampaignService(slotRepository, campaignRepository, eventLogRepository, targetGuard);
    }

    @Test
    void createCampaign_persistsPendingReviewAndDelegatesGuard() {
        PromotionSlot slot = enabledSlot("HOME_HERO", 5);
        given(slotRepository.findBySlotCode("HOME_HERO")).willReturn(Optional.of(slot));
        given(campaignRepository.countActiveBySlot("HOME_HERO", now)).willReturn(0L);
        given(campaignRepository.save(any(PromotionCampaign.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PromotionCampaignService.CreateCampaignCommand cmd = sampleCommand();
        PromotionCampaign saved = service.createCampaign(cmd, "alice", now);

        assertThat(saved.getStatus()).isEqualTo(PromotionCampaignStatus.PENDING_REVIEW);
        verify(targetGuard).assertPromotable(PromotionTargetType.SKILL_BUNDLE, 88L, 120L);
        ArgumentCaptor<PromotionCampaign> captor = ArgumentCaptor.forClass(PromotionCampaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getSubmittedBy()).isEqualTo("alice");
        assertThat(captor.getValue().getSlotCode()).isEqualTo("HOME_HERO");
    }

    @Test
    void createCampaign_rejectsWhenSlotMissing() {
        given(slotRepository.findBySlotCode("HOME_HERO")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaign(sampleCommand(), "alice", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.slot.notFound");

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void createCampaign_rejectsWhenSlotDisabled() {
        PromotionSlot slot = enabledSlot("HOME_HERO", 5);
        slot.setEnabled(false);
        given(slotRepository.findBySlotCode("HOME_HERO")).willReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.createCampaign(sampleCommand(), "alice", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.slot.disabled");
    }

    @Test
    void createCampaign_rejectsWhenSlotIsAtCapacity() {
        PromotionSlot slot = enabledSlot("HOME_HERO", 2);
        given(slotRepository.findBySlotCode("HOME_HERO")).willReturn(Optional.of(slot));
        given(campaignRepository.countActiveBySlot("HOME_HERO", now)).willReturn(2L);

        assertThatThrownBy(() -> service.createCampaign(sampleCommand(), "alice", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.campaign.timeConflict");
    }

    @Test
    void createCampaign_rejectsInvalidTimeWindow() {
        PromotionCampaignService.CreateCampaignCommand reversed = new PromotionCampaignService.CreateCampaignCommand(
                PromotionTargetType.SKILL, 1L, 2L, "HOME_HERO", "T", null, null, null,
                50, now.plus(2, ChronoUnit.DAYS), now, null);

        assertThatThrownBy(() -> service.createCampaign(reversed, "alice", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.campaign.timeWindowInvalid");
    }

    @Test
    void approveCampaign_movesToScheduledWhenStartsLater() {
        PromotionCampaign campaign = pendingCampaign("alice", now.plus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS));
        given(campaignRepository.findById(1L)).willReturn(Optional.of(campaign), Optional.of(campaign));
        given(campaignRepository.updateStatusWithVersion(eq(1L), eq(PromotionCampaignStatus.SCHEDULED),
                eq("admin"), eq("ok"), eq(1))).willReturn(1);

        service.approveCampaign(1L, "ok", "admin", now);

        verify(campaignRepository).updateStatusWithVersion(1L, PromotionCampaignStatus.SCHEDULED, "admin", "ok", 1);
        verify(targetGuard, times(1)).assertPromotable(any(), anyLong(), any());
    }

    @Test
    void approveCampaign_movesToActiveWhenInsideWindow() {
        PromotionCampaign campaign = pendingCampaign("alice", now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS));
        given(campaignRepository.findById(1L)).willReturn(Optional.of(campaign), Optional.of(campaign));
        given(campaignRepository.updateStatusWithVersion(eq(1L), eq(PromotionCampaignStatus.ACTIVE),
                anyString(), any(), anyInt())).willReturn(1);

        service.approveCampaign(1L, null, "admin", now);

        verify(campaignRepository).updateStatusWithVersion(1L, PromotionCampaignStatus.ACTIVE, "admin", null, 1);
    }

    @Test
    void approveCampaign_blocksWhenSubmitterReviewsSelf() {
        PromotionCampaign campaign = pendingCampaign("admin", now.plus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS));
        given(campaignRepository.findById(1L)).willReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.approveCampaign(1L, "ok", "admin", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.campaign.selfReview");

        verify(campaignRepository, never()).updateStatusWithVersion(anyLong(), any(), anyString(), any(), anyInt());
    }

    @Test
    void approveCampaign_throwsOnConcurrentUpdate() {
        PromotionCampaign campaign = pendingCampaign("alice", now.plus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS));
        given(campaignRepository.findById(1L)).willReturn(Optional.of(campaign));
        given(campaignRepository.updateStatusWithVersion(anyLong(), any(), anyString(), any(), anyInt())).willReturn(0);

        assertThatThrownBy(() -> service.approveCampaign(1L, "ok", "admin", now))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.campaign.concurrentUpdate");
    }

    @Test
    void rejectCampaign_movesToRejected() {
        PromotionCampaign campaign = pendingCampaign("alice", now.plus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS));
        given(campaignRepository.findById(1L)).willReturn(Optional.of(campaign), Optional.of(campaign));
        given(campaignRepository.updateStatusWithVersion(eq(1L), eq(PromotionCampaignStatus.REJECTED),
                eq("admin"), eq("not eligible"), eq(1))).willReturn(1);

        service.rejectCampaign(1L, "not eligible", "admin");

        verify(campaignRepository).updateStatusWithVersion(1L, PromotionCampaignStatus.REJECTED, "admin", "not eligible", 1);
    }

    @Test
    void recordEvent_persistsLog() {
        given(campaignRepository.findById(7L)).willReturn(Optional.of(pendingCampaign("alice",
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))));
        given(eventLogRepository.save(any(PromotionEventLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PromotionEventLog logged = service.recordEvent(7L, PromotionEventType.CLICK, "user-1", null, "req-1");

        assertThat(logged.getEventType()).isEqualTo(PromotionEventType.CLICK);
        assertThat(logged.getUserId()).isEqualTo("user-1");
        verify(eventLogRepository).save(any(PromotionEventLog.class));
    }

    @Test
    void runScheduledSweep_returnsActivatedAndEndedCounts() {
        given(campaignRepository.markScheduledAsActive(now)).willReturn(3);
        given(campaignRepository.markActiveAsEnded(now)).willReturn(1);

        PromotionCampaignService.SchedulerSweepResult result = service.runScheduledSweep(now);

        assertThat(result.activated()).isEqualTo(3);
        assertThat(result.ended()).isEqualTo(1);
    }

    private PromotionSlot enabledSlot(String code, int max) {
        PromotionSlot slot = new PromotionSlot(code, "首页首屏",
                "[\"SKILL\",\"SKILL_BUNDLE\"]", max);
        slot.setEnabled(true);
        return slot;
    }

    private PromotionCampaign pendingCampaign(String submitter, Instant startsAt, Instant endsAt) {
        PromotionCampaign campaign = new PromotionCampaign(
                PromotionTargetType.SKILL_BUNDLE, 88L, "HOME_HERO",
                "Title", 80, startsAt, endsAt, submitter);
        campaign.setStatus(PromotionCampaignStatus.PENDING_REVIEW);
        setField(campaign, "id", 1L);
        return campaign;
    }

    private PromotionCampaignService.CreateCampaignCommand sampleCommand() {
        return new PromotionCampaignService.CreateCampaignCommand(
                PromotionTargetType.SKILL_BUNDLE, 88L, 120L,
                "HOME_HERO", "Title", "Subtitle", 501L, 502L,
                80, now.plus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS),
                "Featured");
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
