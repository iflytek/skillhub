package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;

    public NamespaceMemberService(NamespaceMemberRepository namespaceMemberRepository,
                                  NamespaceService namespaceService,
                                  NamespaceAccessPolicy namespaceAccessPolicy,
                                  NamespaceRepository namespaceRepository,
                                  UserAccountRepository userAccountRepository,
                                  AuditLogService auditLogService) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceService = namespaceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public NamespaceMember addMember(Long namespaceId, String userId, NamespaceRole role, String operatorUserId) {
        lockUserThenNamespace(userId, namespaceId);
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

    /** A TEAM owner/admin grants a MEMBER a namespace-local review exemption. */
    @Transactional
    public NamespaceMember setReviewExempt(Long namespaceId, String userId, boolean enabled, String operatorUserId) {
        lockUserThenNamespace(userId, namespaceId);
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));
        if (member.getRole() != NamespaceRole.MEMBER) {
            throw new DomainBadRequestException("error.namespace.member.reviewExempt.memberOnly");
        }
        member.setReviewExempt(enabled);
        NamespaceMember saved = namespaceMemberRepository.save(member);
        auditLogService.record(operatorUserId, "SET_REVIEW_EXEMPT", "NAMESPACE_MEMBER", saved.getId(),
                null, null, null, AuditDetail.of("enabled", enabled, "namespaceId", namespaceId));
        return saved;
    }

    @Transactional
    public Namespace setTrustedPublishEnabled(Long namespaceId, boolean enabled, String operatorUserId) {
        userAccountRepository.findLockedById(operatorUserId);
        namespaceRepository.findLockedById(namespaceId);
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);
        Namespace namespace = namespaceService.getNamespace(namespaceId);
        namespace.setTrustedPublishEnabled(enabled);
        Namespace saved = namespaceRepository.save(namespace);
        auditLogService.record(operatorUserId, "SET_TRUSTED_PUBLISH_POLICY", "NAMESPACE", namespaceId,
                null, null, null, AuditDetail.of("enabled", enabled));
        return saved;
    }

    @Transactional
    public void removeMember(Long namespaceId, String userId, String operatorUserId) {
        lockUserThenNamespace(userId, namespaceId);
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
        lockUserThenNamespace(userId, namespaceId);
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        if (newRole == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.setDirect");
        }

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));

        if (newRole != NamespaceRole.MEMBER) { member.setReviewExempt(false); }
        member.setRole(newRole);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void transferOwnership(Long namespaceId, String currentOwnerId, String newOwnerId) {
        if (currentOwnerId.compareTo(newOwnerId) <= 0) {
            userAccountRepository.findLockedById(currentOwnerId);
            userAccountRepository.findLockedById(newOwnerId);
        } else {
            userAccountRepository.findLockedById(newOwnerId);
            userAccountRepository.findLockedById(currentOwnerId);
        }
        namespaceRepository.findLockedById(namespaceId);
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

    private void lockUserThenNamespace(String userId, Long namespaceId) {
        userAccountRepository.findLockedById(userId);
        namespaceRepository.findLockedById(namespaceId);
    }
}
