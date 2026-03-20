package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RecipientResolver {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;

    public RecipientResolver(NamespaceMemberRepository namespaceMemberRepository,
                              UserRoleBindingRepository userRoleBindingRepository) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
    }

    public List<String> resolveNamespaceAdmins(Long namespaceId) {
        return namespaceMemberRepository
                .findByNamespaceIdAndRoleIn(namespaceId, Set.of(NamespaceRole.OWNER, NamespaceRole.ADMIN))
                .stream()
                .map(NamespaceMember::getUserId)
                .toList();
    }

    public List<String> resolvePlatformSkillAdmins() {
        return userRoleBindingRepository.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN"))
                .stream()
                .map(binding -> binding.getUserId())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }
}
