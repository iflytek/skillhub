package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for namespace membership records and paged member listings.
 */
@Repository
public interface NamespaceMemberJpaRepository
        extends JpaRepository<NamespaceMember, Long>, NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, String userId);
    List<NamespaceMember> findByUserId(String userId);
    Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
    List<NamespaceMember> findByNamespaceIdAndRoleIn(Long namespaceId, Collection<NamespaceRole> roles);
    void deleteByNamespaceIdAndUserId(Long namespaceId, String userId);
}
