package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;

import java.util.*;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Component;

/** Loads authoritative account and membership facts in batches before selecting recipients. */
@Component
public class SubscriptionRecipientEligibility {
    private final UserAccountRepository accountRepository;
    private final NamespaceMemberRepository memberRepository;
    private final SubscriptionMetadataAccessPolicy policy;

    public SubscriptionRecipientEligibility(UserAccountRepository accountRepository,
                                            NamespaceMemberRepository memberRepository,
                                            SubscriptionMetadataAccessPolicy policy) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.policy = policy;
    }

    public List<String> currentRecipients(Skill skill, Namespace namespace, List<String> candidateIds) {
        return eligible(skill, namespace, candidateIds,
                (account, roles) -> policy.canAccessCurrent(skill, namespace, account, roles));
    }

    public List<String> yankedRecipients(Skill skill, Namespace namespace, List<String> candidateIds,
                                         boolean wasPublished) {
        return eligible(skill, namespace, candidateIds,
                (account, roles) -> policy.canAccessYankedPublication(skill, namespace, account, roles, wasPublished));
    }

    private List<String> eligible(Skill skill, Namespace namespace, List<String> candidateIds,
                                  BiPredicate<UserAccount, Map<Long, NamespaceRole>> predicate) {
        List<String> ids = candidateIds.stream().distinct().toList();
        if (ids.isEmpty()) return List.of();
        Map<String, UserAccount> accounts = new HashMap<>();
        accountRepository.findByIdIn(ids).forEach(account -> accounts.put(account.getId(), account));
        Map<String, NamespaceRole> roles = new HashMap<>();
        memberRepository.findByNamespaceIdAndUserIdIn(skill.getNamespaceId(), ids)
                .forEach(member -> roles.put(member.getUserId(), member.getRole()));
        return ids.stream().filter(id -> {
            NamespaceRole role = roles.get(id);
            Map<Long, NamespaceRole> roleMap = role == null ? Map.of() : Map.of(skill.getNamespaceId(), role);
            return predicate.test(accounts.get(id), roleMap);
        }).toList();
    }
}
