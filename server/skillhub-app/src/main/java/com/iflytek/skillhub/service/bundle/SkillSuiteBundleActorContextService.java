package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves fresh persisted authorization for asynchronous Bundle boundaries. */
@Service
public class SkillSuiteBundleActorContextService {

    private final UserAccountRepository userRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final RbacService rbacService;

    public SkillSuiteBundleActorContextService(
            UserAccountRepository userRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            RbacService rbacService
    ) {
        this.userRepository = userRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public ActorContext requireCurrent(String actorId) {
        if (userRepository.findById(actorId).filter(user -> user.isActive()).isEmpty()) {
            throw new DomainForbiddenException("error.suite.bundle.actor.inactive");
        }
        Map<Long, NamespaceRole> namespaceRoles = namespaceMemberRepository.findByUserId(actorId).stream()
                .collect(Collectors.toUnmodifiableMap(
                        member -> member.getNamespaceId(), member -> member.getRole()));
        Set<String> platformRoles = Set.copyOf(rbacService.getUserRoleCodes(actorId));
        return new ActorContext(namespaceRoles, platformRoles);
    }

    public record ActorContext(
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
    }
}
