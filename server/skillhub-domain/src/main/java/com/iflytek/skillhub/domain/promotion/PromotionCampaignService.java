package com.iflytek.skillhub.domain.promotion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

        PromotionCampaign campaign = new PromotionCampaign(
                command.targetType(),
                command.targetId(),
                slot.getSlotCode(),
                command.title(),
                command.priority(),
                command.startsAt(),
                command.endsAt(),
                submitter,
                now
        );
        campaign.setSubtitle(command.subtitle());
        campaign.setCoverMediaId(command.coverMediaId());
        campaign.setDemoMediaId(command.demoMediaId());
        campaign.setReason(command.reason());
        campaign.setTargetVersionId(command.targetVersionId());
        campaign.setStatus(PromotionCampaignStatus.PENDING_REVIEW);
        assertSlotCapacityAvailable(campaign, now);
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
        assertSlotCapacityAvailable(campaign, now);

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
        int activated = activateEligibleScheduledCampaigns(now);
        int ended = campaignRepository.markActiveAsEnded(now);
        return new SchedulerSweepResult(activated, ended);
    }

    private void assertSlotCapacityAvailable(PromotionCampaign campaign, Instant now) {
        if (!now.isBefore(campaign.getEndsAt())) {
            return;
        }
        PromotionSlot slot = slotRepository.findBySlotCode(campaign.getSlotCode())
                .orElseThrow(() -> new PromotionException("error.promotion.slot.notFound"));
        Instant checkedStartsAt = max(campaign.getStartsAt(), now);
        if (!campaign.getEndsAt().isAfter(checkedStartsAt)) {
            return;
        }
        List<PromotionCampaign> candidates = campaignRepository.findCapacityCandidates(
                campaign.getSlotCode(), checkedStartsAt, campaign.getEndsAt());
        if (exceedsCapacity(candidates, campaign, checkedStartsAt, campaign.getEndsAt(), slot.getMaxActiveItems())) {
            throw new PromotionException("error.promotion.campaign.timeConflict");
        }
    }

    private int activateEligibleScheduledCampaigns(Instant now) {
        List<PromotionCampaign> due = campaignRepository.findReadyToActivate(now).stream()
                .sorted(Comparator.comparing(PromotionCampaign::getStartsAt)
                        .thenComparing(Comparator.comparing(PromotionCampaign::getPriority).reversed())
                        .thenComparing(PromotionCampaign::getId))
                .toList();

        int activated = 0;
        for (PromotionCampaign campaign : due) {
            try {
                assertActivationCapacityAvailable(campaign, now);
                int updated = campaignRepository.updateStatusWithVersion(
                        campaign.getId(), PromotionCampaignStatus.ACTIVE,
                        campaign.getReviewedBy(), campaign.getReviewComment(), campaign.getVersion());
                activated += updated;
            } catch (PromotionException ignored) {
                // Keep over-capacity scheduled items queued for an operator-visible fix.
            }
        }
        return activated;
    }

    private void assertActivationCapacityAvailable(PromotionCampaign campaign, Instant now) {
        PromotionSlot slot = slotRepository.findBySlotCode(campaign.getSlotCode())
                .orElseThrow(() -> new PromotionException("error.promotion.slot.notFound"));
        long activeNow = campaignRepository.findActiveBySlot(campaign.getSlotCode(), now).stream()
                .filter(c -> !Objects.equals(c.getId(), campaign.getId()))
                .count();
        if (activeNow >= slot.getMaxActiveItems()) {
            throw new PromotionException("error.promotion.campaign.timeConflict");
        }
    }

    private boolean exceedsCapacity(List<PromotionCampaign> existing,
                                    PromotionCampaign candidate,
                                    Instant startsAt,
                                    Instant endsAt,
                                    int maxActiveItems) {
        List<Instant> boundaries = new ArrayList<>();
        boundaries.add(startsAt);
        boundaries.add(endsAt);
        for (PromotionCampaign campaign : existing) {
            if (Objects.equals(campaign.getId(), candidate.getId())) {
                continue;
            }
            Instant overlapStart = max(campaign.getStartsAt(), startsAt);
            Instant overlapEnd = min(campaign.getEndsAt(), endsAt);
            if (overlapStart.isBefore(overlapEnd)) {
                boundaries.add(overlapStart);
                boundaries.add(overlapEnd);
            }
        }

        return boundaries.stream()
                .distinct()
                .filter(point -> point.isBefore(endsAt))
                .anyMatch(point -> overlappingCount(existing, candidate, point) > maxActiveItems);
    }

    private int overlappingCount(List<PromotionCampaign> existing, PromotionCampaign candidate, Instant point) {
        int count = isActiveAt(candidate, point) ? 1 : 0;
        for (PromotionCampaign campaign : existing) {
            if (!Objects.equals(campaign.getId(), candidate.getId()) && isActiveAt(campaign, point)) {
                count++;
            }
        }
        return count;
    }

    private boolean isActiveAt(PromotionCampaign campaign, Instant point) {
        return !campaign.getStartsAt().isAfter(point) && campaign.getEndsAt().isAfter(point);
    }

    private Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
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
