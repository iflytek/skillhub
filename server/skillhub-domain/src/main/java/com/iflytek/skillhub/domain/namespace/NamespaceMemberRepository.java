package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for namespace membership lookups and member administration.
 */
public interface NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, String userId);
    List<NamespaceMember> findByUserId(String userId);
    Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
    List<NamespaceMember> findByNamespaceIdAndRoleIn(Long namespaceId, Collection<NamespaceRole> roles);
    NamespaceMember save(NamespaceMember member);
    void deleteByNamespaceIdAndUserId(Long namespaceId, String userId);
}
