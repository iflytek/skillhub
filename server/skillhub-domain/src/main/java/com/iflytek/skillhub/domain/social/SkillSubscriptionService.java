package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.social.event.SkillSubscribedEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnsubscribedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SkillSubscriptionService {
    private final SkillSubscriptionRepository subscriptionRepository;
    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository memberRepository;
    private final UserAccountRepository accountRepository;
    private final SubscriptionMetadataAccessPolicy accessPolicy;

    public SkillSubscriptionService(SkillSubscriptionRepository subscriptionRepository,
                                    SkillRepository skillRepository,
                                    ApplicationEventPublisher eventPublisher,
                                    NamespaceRepository namespaceRepository,
                                    NamespaceMemberRepository memberRepository,
                                    UserAccountRepository accountRepository,
                                    SubscriptionMetadataAccessPolicy accessPolicy) {
        this.subscriptionRepository = subscriptionRepository;
        this.skillRepository = skillRepository;
        this.eventPublisher = eventPublisher;
        this.namespaceRepository = namespaceRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public void subscribe(Long skillId, String userId) {
        var skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillId));
        UserAccount account = accountRepository.findById(userId).orElse(null);
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId()).orElse(null);
        var role = memberRepository.findByNamespaceIdAndUserId(skill.getNamespaceId(), userId)
                .map(NamespaceMember::getRole);
        Map<Long, NamespaceRole> roles = role.map(value -> Map.of(skill.getNamespaceId(), value)).orElse(Map.of());
        if (!accessPolicy.canAccessCurrent(skill, namespace, account, roles)) {
            throw new DomainForbiddenException("error.skill.subscription.noPermission");
        }
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
