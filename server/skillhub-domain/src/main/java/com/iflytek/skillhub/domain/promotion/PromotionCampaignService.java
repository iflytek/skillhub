package com.iflytek.skillhub.domain.promotion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for operational promotion management. Encapsulates the campaign
 * state machine (DRAFT -&gt; PENDING_REVIEW -&gt; SCHEDULED|ACTIVE|ENDED|REJECTED), slot
 * capacity rules, and target reachability rules described in the design document.
 *
 * <p>The service is intentionally framework-agnostic so it can be unit-tested without
 * Spring context — wiring lives in skillhub-app.
 */
public class PromotionCampaignService {

    private final PromotionSlotRepository slotRepository;
    private final PromotionCampaignRepository campaignRepository;
    private final PromotionEventLogRepository eventLogRepository;
    private final PromotionTargetGuard targetGuard;

    public PromotionCampaignService(PromotionSlotRepository slotRepository,
                                    PromotionCampaignRepository campaignRepository,
                                    PromotionEventLogRepository eventLogRepository,
                                    PromotionTargetGuard targetGuard) {
        this.slotRepository = slotRepository;
        this.campaignRepository = campaignRepository;
        this.eventLogRepository = eventLogRepository;
        this.targetGuard = targetGuard;
    }

    public PromotionCampaign createCampaign(CreateCampaignCommand command, String submitter, Instant now) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(submitter, "submitter");

        if (command.startsAt() == null || command.endsAt() == null) {
            throw new PromotionException("error.promotion.campaign.windowRequired");
        }
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new PromotionException("error.promotion.campaign.timeWindowInvalid");
        }
        if (command.priority() < 0 || command.priority() > 100) {
            throw new PromotionException("error.promotion.campaign.priorityOutOfRange");
        }

        PromotionSlot slot = slotRepository.findBySlotCode(command.slotCode())
                .orElseThrow(() -> new PromotionException("error.promotion.slot.notFound"));
        if (!slot.isEnabled()) {
            throw new PromotionException("error.promotion.slot.disabled");
        }

        targetGuard.assertPromotable(command.targetType(), command.targetId(), command.targetVersionId());

        long currentActive = campaignRepository.countActiveBySlot(slot.getSlotCode(), now);
        if (currentActive >= slot.getMaxActiveItems()) {
            throw new PromotionException("error.promotion.campaign.timeConflict");
        }

        PromotionCampaign campaign = new PromotionCampaign(
                command.targetType(),
                command.targetId(),
                slot.getSlotCode(),
                command.title(),
                command.priority(),
                command.startsAt(),
                command.endsAt(),
                submitter
        );
        campaign.setSubtitle(command.subtitle());
        campaign.setCoverMediaId(command.coverMediaId());
        campaign.setDemoMediaId(command.demoMediaId());
        campaign.setReason(command.reason());
        campaign.setTargetVersionId(command.targetVersionId());
        campaign.setStatus(PromotionCampaignStatus.PENDING_REVIEW);
        return campaignRepository.save(campaign);
    }

    public PromotionCampaign approveCampaign(Long id, String comment, String reviewer, Instant now) {
        PromotionCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new PromotionException("error.promotion.campaign.notFound"));
        if (campaign.getStatus() != PromotionCampaignStatus.PENDING_REVIEW) {
            throw new PromotionException("error.promotion.campaign.notPendingReview");
        }
        if (campaign.getSubmittedBy().equals(reviewer)) {
            throw new PromotionException("error.promotion.campaign.selfReview");
        }
        targetGuard.assertPromotable(campaign.getTargetType(), campaign.getTargetId(), campaign.getTargetVersionId());

        PromotionCampaignStatus next = computeApprovedState(campaign, now);
        int updated = campaignRepository.updateStatusWithVersion(
                campaign.getId(), next, reviewer, comment, campaign.getVersion());
        if (updated == 0) {
            throw new PromotionException("error.promotion.campaign.concurrentUpdate");
        }
        return campaignRepository.findById(id).orElseThrow();
    }

    public PromotionCampaign rejectCampaign(Long id, String comment, String reviewer) {
        PromotionCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new PromotionException("error.promotion.campaign.notFound"));
        if (campaign.getStatus() != PromotionCampaignStatus.PENDING_REVIEW) {
            throw new PromotionException("error.promotion.campaign.notPendingReview");
        }
        int updated = campaignRepository.updateStatusWithVersion(
                campaign.getId(), PromotionCampaignStatus.REJECTED, reviewer, comment, campaign.getVersion());
        if (updated == 0) {
            throw new PromotionException("error.promotion.campaign.concurrentUpdate");
        }
        return campaignRepository.findById(id).orElseThrow();
    }

    public List<PromotionCampaign> listSlotItems(String slotCode, Instant now) {
        if (slotRepository.findBySlotCode(slotCode).isEmpty()) {
            throw new PromotionException("error.promotion.slot.notFound");
        }
        return campaignRepository.findActiveBySlot(slotCode, now);
    }

    public PromotionEventLog recordEvent(Long campaignId, PromotionEventType type,
                                         String userId, String anonymousId, String requestId) {
        Optional<PromotionCampaign> campaign = campaignRepository.findById(campaignId);
        if (campaign.isEmpty()) {
            throw new PromotionException("error.promotion.campaign.notFound");
        }
        return eventLogRepository.save(new PromotionEventLog(campaignId, type, userId, anonymousId, requestId));
    }

    public SchedulerSweepResult runScheduledSweep(Instant now) {
        int activated = campaignRepository.markScheduledAsActive(now);
        int ended = campaignRepository.markActiveAsEnded(now);
        return new SchedulerSweepResult(activated, ended);
    }

    private PromotionCampaignStatus computeApprovedState(PromotionCampaign campaign, Instant now) {
        if (now.isBefore(campaign.getStartsAt())) {
            return PromotionCampaignStatus.SCHEDULED;
        }
        if (now.isBefore(campaign.getEndsAt())) {
            return PromotionCampaignStatus.ACTIVE;
        }
        return PromotionCampaignStatus.ENDED;
    }

    public record CreateCampaignCommand(PromotionTargetType targetType,
                                        Long targetId,
                                        Long targetVersionId,
                                        String slotCode,
                                        String title,
                                        String subtitle,
                                        Long coverMediaId,
                                        Long demoMediaId,
                                        int priority,
                                        Instant startsAt,
                                        Instant endsAt,
                                        String reason) {}

    public record SchedulerSweepResult(int activated, int ended) {}
}
