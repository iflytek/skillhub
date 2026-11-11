package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.review.ReviewAutoPublicationHandoff;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    private final ReviewAutoPublicationHandoff autoPublicationHandoff;

    public NamespaceMemberService(NamespaceMemberRepository namespaceMemberRepository,
                                  NamespaceService namespaceService,
                                  NamespaceAccessPolicy namespaceAccessPolicy,
                                  NamespaceRepository namespaceRepository,
                                  UserAccountRepository userAccountRepository,
                                  AuditLogService auditLogService,
                                  ReviewAutoPublicationHandoff autoPublicationHandoff) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceService = namespaceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
        this.autoPublicationHandoff = autoPublicationHandoff;
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

    /**
     * Grants or revokes team-only automatic publication for one ordinary member of an ACTIVE TEAM
     * namespace. Revocation irreversibly hands pending eligible work back to normal review.
     */
    @Transactional
    public NamespaceMember setTeamAutoPublication(Long namespaceId,
                                                  String userId,
                                                  boolean granted,
                                                  String operatorUserId) {
        lockUserThenNamespace(userId, namespaceId);
        assertMemberMutationAllowed(namespaceId);
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));
        if (granted && member.getRole() != NamespaceRole.MEMBER) {
            throw new DomainBadRequestException("error.namespace.member.teamAutoPublication.memberOnly");
        }
        UUID before = member.getTeamAutoPublicationGrantId();
        UUID after = granted ? member.grantTeamAutoPublication() : null;
        if (!granted) {
            member.revokeTeamAutoPublication();
        }
        if (Objects.equals(before, after)) {
            return member;
        }
        NamespaceMember saved = namespaceMemberRepository.save(member);
        int handedOff = granted ? 0 : autoPublicationHandoff.handOffMember(namespaceId, userId);
        recordGrantChange(operatorUserId, saved, granted ? "GRANT" : "REVOKE", before, after, handedOff);
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

        revokeForEligibilityLoss(member, operatorUserId, "MEMBER_REMOVED");
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

        if (newRole != NamespaceRole.MEMBER) {
            revokeForEligibilityLoss(member, operatorUserId, "ROLE_CHANGED");
        }
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

        revokeForEligibilityLoss(newOwner, currentOwnerId, "ROLE_CHANGED");
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

    private void revokeForEligibilityLoss(NamespaceMember member, String operatorUserId, String reason) {
        UUID before = member.revokeTeamAutoPublication();
        if (before == null) {
            return;
        }
        namespaceMemberRepository.save(member);
        int handedOff = autoPublicationHandoff.handOffMember(member.getNamespaceId(), member.getUserId());
        recordGrantChange(operatorUserId, member, reason, before, null, handedOff);
    }

    private void recordGrantChange(String operatorUserId,
                                   NamespaceMember member,
                                   String reason,
                                   UUID before,
                                   UUID after,
                                   int handedOffTasks) {
        auditLogService.record(operatorUserId, "TEAM_AUTO_PUBLICATION_CHANGE", "NAMESPACE_MEMBER", member.getId(),
                null, null, null, AuditDetail.builder()
                        .put("namespaceId", member.getNamespaceId())
                        .put("userId", member.getUserId())
                        .put("reason", reason)
                        .put("granted", after != null)
                        .put("beforeGrantId", before == null ? null : before.toString())
                        .put("afterGrantId", after == null ? null : after.toString())
                        .put("handedOffTasks", handedOffTasks)
                        .build());
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
