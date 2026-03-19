package com.iflytek.skillhub.domain.namespace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures newly active users belong to the built-in global namespace.
 */
@Service
public class GlobalNamespaceMembershipService {

    private static final String GLOBAL_NAMESPACE_SLUG = "global";

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;

    public GlobalNamespaceMembershipService(NamespaceRepository namespaceRepository,
                                            NamespaceMemberRepository namespaceMemberRepository) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    @Transactional
    public void ensureMember(String userId) {
        Namespace globalNamespace = namespaceRepository.findBySlug(GLOBAL_NAMESPACE_SLUG)
                .orElseThrow(() -> new IllegalStateException("Missing built-in global namespace"));

        namespaceMemberRepository.findByNamespaceIdAndUserId(globalNamespace.getId(), userId)
                .orElseGet(() -> namespaceMemberRepository.save(
                        new NamespaceMember(globalNamespace.getId(), userId, NamespaceRole.MEMBER)
                ));
    }
}
