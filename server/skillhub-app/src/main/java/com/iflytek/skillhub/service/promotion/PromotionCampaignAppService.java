package com.iflytek.skillhub.service.promotion;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.promotion.PromotionCampaign;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignRepository;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignService;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus;
import com.iflytek.skillhub.domain.promotion.PromotionEventType;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.promotion.CreatePromotionCampaignRequest;
import com.iflytek.skillhub.dto.promotion.PromotionCampaignResponse;
import com.iflytek.skillhub.dto.promotion.PromotionSlotItemResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Application-level façade that exposes promotion campaign use cases to controllers.
 * Hides the domain {@link PromotionCampaignService} behind DTO conversions, request
 * paging, and target URL synthesis.
 */
@Service
public class PromotionCampaignAppService {

    private final PromotionCampaignService domainService;
    private final PromotionCampaignRepository campaignRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final Clock clock;

    public PromotionCampaignAppService(PromotionCampaignService domainService,
                                       PromotionCampaignRepository campaignRepository,
                                       SkillRepository skillRepository,
                                       NamespaceRepository namespaceRepository,
                                       Clock clock) {
        this.domainService = domainService;
        this.campaignRepository = campaignRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.clock = clock;
    }

    @Transactional
    public PromotionCampaignResponse createCampaign(CreatePromotionCampaignRequest request, String submitter) {
        PromotionCampaignService.CreateCampaignCommand command = new PromotionCampaignService.CreateCampaignCommand(
                request.targetType(), request.targetId(), request.targetVersionId(),
                request.slotCode(), request.title(), request.subtitle(),
                request.coverMediaId(), request.demoMediaId(),
                request.priority(), request.startsAt(), request.endsAt(),
                request.reason()
        );
        PromotionCampaign saved = domainService.createCampaign(command, submitter, clock.instant());
        return PromotionCampaignResponse.from(saved);
    }

    @Transactional
    public PromotionCampaignResponse approve(Long id, String comment, String reviewer) {
        return PromotionCampaignResponse.from(domainService.approveCampaign(id, comment, reviewer, clock.instant()));
    }

    @Transactional
    public PromotionCampaignResponse reject(Long id, String comment, String reviewer) {
        return PromotionCampaignResponse.from(domainService.rejectCampaign(id, comment, reviewer));
    }

    @Transactional(readOnly = true)
    public List<PromotionSlotItemResponse> listSlotItems(String slotCode) {
        return domainService.listSlotItems(slotCode, clock.instant()).stream()
                .map(c -> PromotionSlotItemResponse.from(c, resolveTargetUrl(c)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionCampaignResponse> listByStatus(PromotionCampaignStatus status, int page, int size) {
        return PageResponse.from(campaignRepository.findByStatus(status, PageRequest.of(page, size))
                .map(PromotionCampaignResponse::from));
    }

    @Transactional
    public void recordEvent(Long campaignId, PromotionEventType type,
                            String userId, String anonymousId, String requestId) {
        domainService.recordEvent(campaignId, type, userId, anonymousId, requestId);
    }

    @Transactional
    public PromotionCampaignService.SchedulerSweepResult sweepLifecycle() {
        return domainService.runScheduledSweep(clock.instant());
    }

    private String resolveTargetUrl(PromotionCampaign campaign) {
        if (campaign.getTargetType() == PromotionTargetType.SKILL_BUNDLE) {
            return "/bundles/" + campaign.getTargetId();
        }
        Optional<Skill> skill = skillRepository.findById(campaign.getTargetId());
        if (skill.isEmpty()) {
            return null;
        }
        Skill s = skill.get();
        Optional<Namespace> ns = namespaceRepository.findById(s.getNamespaceId());
        String namespaceSlug = ns.map(Namespace::getSlug).orElse("global");
        return "/space/" + namespaceSlug + "/" + s.getSlug();
    }
}
