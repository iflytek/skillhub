package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.event.SkillSubscribedEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnsubscribedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkillSubscriptionService {
    private final SkillSubscriptionRepository subscriptionRepository;
    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillSubscriptionService(SkillSubscriptionRepository subscriptionRepository,
                                    SkillRepository skillRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.skillRepository = skillRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void subscribe(Long skillId, String userId) {
        ensureSkillExists(skillId);
        if (subscriptionRepository.findBySkillIdAndUserId(skillId, userId).isPresent()) {
            return; // idempotent
        }
        subscriptionRepository.save(new SkillSubscription(skillId, userId));
        skillRepository.incrementSubscriptionCount(skillId);
        eventPublisher.publishEvent(new SkillSubscribedEvent(skillId, userId));
    }

    @Transactional
    public void unsubscribe(Long skillId, String userId) {
        ensureSkillExists(skillId);
        subscriptionRepository.findBySkillIdAndUserId(skillId, userId).ifPresent(subscription -> {
            subscriptionRepository.delete(subscription);
            skillRepository.decrementSubscriptionCount(skillId);
            eventPublisher.publishEvent(new SkillUnsubscribedEvent(skillId, userId));
        });
    }

    public boolean isSubscribed(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return subscriptionRepository.findBySkillIdAndUserId(skillId, userId).isPresent();
    }

    public List<String> findSubscribersBySkillId(Long skillId) {
        return subscriptionRepository.findAllBySkillId(skillId).stream()
                .map(SkillSubscription::getUserId)
                .distinct()
                .toList();
    }

    private void ensureSkillExists(Long skillId) {
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new DomainNotFoundException("skill.not_found", skillId);
        }
    }
}
