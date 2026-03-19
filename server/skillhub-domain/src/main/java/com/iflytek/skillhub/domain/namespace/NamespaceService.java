package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Domain service for namespace lifecycle and membership-gated mutations.
 */
@Service
public class NamespaceService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final NamespaceAccessPolicy namespaceAccessPolicy;

    public NamespaceService(NamespaceRepository namespaceRepository,
                           NamespaceMemberRepository namespaceMemberRepository,
                           NamespaceAccessPolicy namespaceAccessPolicy) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
    }

    /**
     * Creates a team namespace and grants the creator the owner role in the
     * same transaction.
     */
    @Transactional
    public Namespace createNamespace(String slug, String displayName, String description, String creatorUserId) {
        SlugValidator.validate(slug);

        if (namespaceRepository.findBySlug(slug).isPresent()) {
            throw new DomainBadRequestException("error.namespace.slug.exists", slug);
        }

        Namespace namespace = new Namespace(slug, displayName, creatorUserId);
        namespace.setDescription(description);
        namespace.setType(NamespaceType.TEAM);
        namespace = namespaceRepository.save(namespace);

        NamespaceMember ownerMember = new NamespaceMember(namespace.getId(), creatorUserId, NamespaceRole.OWNER);
        namespaceMemberRepository.save(ownerMember);

        return namespace;
    }

    /**
     * Updates mutable namespace profile fields after policy and role checks.
     */
    @Transactional
    public Namespace updateNamespace(Long namespaceId, String displayName, String description, String avatarUrl,
                                     String operatorUserId) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.id.notFound", namespaceId));
        assertNotImmutable(namespace);
        assertAdminOrOwner(namespaceId, operatorUserId);
        assertWritable(namespace);

        if (displayName != null) {
            namespace.setDisplayName(displayName);
        }
        if (description != null) {
            namespace.setDescription(description);
        }
        if (avatarUrl != null) {
            namespace.setAvatarUrl(avatarUrl);
        }

        return namespaceRepository.save(namespace);
    }

    /**
     * Loads a namespace by slug and fails with a business exception when it is
     * missing.
     */
    public Namespace getNamespaceBySlug(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    /**
     * Returns archived namespaces only to callers that already belong to them;
     * all other callers see archived namespaces as not found.
     */
    public Namespace getNamespaceBySlugForRead(String slug, String userId, Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = getNamespaceBySlug(slug);
        if (namespace.getStatus() != NamespaceStatus.ARCHIVED) {
            return namespace;
        }
        if (userId != null && userNsRoles != null && userNsRoles.containsKey(namespace.getId())) {
            return namespace;
        }
        throw new DomainBadRequestException("error.namespace.slug.notFound", slug);
    }

    public Namespace getNamespace(Long namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.id.notFound", namespaceId));
    }

    /**
     * Ensures the caller holds an owner or admin membership in the namespace.
     */
    public void assertAdminOrOwner(Long namespaceId, String userId) {
        NamespaceRole role = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole)
                .orElseThrow(() -> new DomainForbiddenException("error.namespace.membership.required"));
        if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
    }

    /**
     * Ensures the caller is at least a member of the namespace.
     */
    public void assertMember(Long namespaceId, String userId) {
        namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainForbiddenException("error.namespace.membership.required"));
    }

    void assertMutable(Namespace namespace) {
        assertNotImmutable(namespace);
        assertWritable(namespace);
    }

    void assertNotImmutable(Namespace namespace) {
        if (namespaceAccessPolicy.isImmutable(namespace)) {
            throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
        }
    }

    private void assertWritable(Namespace namespace) {
        if (!namespaceAccessPolicy.canMutateSettings(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }
    }
}
