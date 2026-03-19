package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages namespace membership additions, removals, and role changes under the
 * namespace governance rules.
 */
@Service
public class NamespaceMemberService {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final NamespaceService namespaceService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;

    public NamespaceMemberService(NamespaceMemberRepository namespaceMemberRepository,
                                  NamespaceService namespaceService,
                                  NamespaceAccessPolicy namespaceAccessPolicy) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceService = namespaceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
    }

    @Transactional
    public NamespaceMember addMember(Long namespaceId, String userId, NamespaceRole role, String operatorUserId) {
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        if (role == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.assignDirect");
        }

        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId).isPresent()) {
            throw new DomainBadRequestException("error.namespace.member.alreadyExists");
        }

        NamespaceMember member = new NamespaceMember(namespaceId, userId, role);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long namespaceId, String userId, String operatorUserId) {
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));

        if (member.getRole() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.remove");
        }

        namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespaceId, userId);
    }

    @Transactional
    public NamespaceMember updateMemberRole(Long namespaceId, String userId, NamespaceRole newRole, String operatorUserId) {
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        if (newRole == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.setDirect");
        }

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));

        member.setRole(newRole);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void transferOwnership(Long namespaceId, String currentOwnerId, String newOwnerId) {
        Namespace namespace = namespaceService.getNamespace(namespaceId);
        if (!namespaceAccessPolicy.canTransferOwnership(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }

        NamespaceMember currentOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.current.notFound"));

        if (currentOwner.getRole() != NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.owner.current.invalid");
        }

        NamespaceMember newOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.new.notFound"));

        currentOwner.setRole(NamespaceRole.ADMIN);
        newOwner.setRole(NamespaceRole.OWNER);

        namespaceMemberRepository.save(currentOwner);
        namespaceMemberRepository.save(newOwner);
    }

    public Optional<NamespaceRole> getMemberRole(Long namespaceId, String userId) {
        return namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole);
    }

    public Page<NamespaceMember> listMembers(Long namespaceId, Pageable pageable) {
        return namespaceMemberRepository.findByNamespaceId(namespaceId, pageable);
    }

    private void assertMemberMutationAllowed(Long namespaceId) {
        Namespace namespace = namespaceService.getNamespace(namespaceId);
        if (!namespaceAccessPolicy.canManageMembers(namespace)) {
            if (namespaceAccessPolicy.isImmutable(namespace)) {
                throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
            }
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }
    }
}
