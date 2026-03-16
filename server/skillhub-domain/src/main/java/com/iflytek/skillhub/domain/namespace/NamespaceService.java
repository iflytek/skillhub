package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Namespace updateNamespace(Long namespaceId, String displayName, String description, String avatarUrl,
                                     String operatorUserId) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.id.notFound", namespaceId));
        assertAdminOrOwner(namespaceId, operatorUserId);
        assertMutable(namespace);

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

    public Namespace getNamespaceBySlug(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    public Namespace getNamespace(Long namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.id.notFound", namespaceId));
    }

    void assertAdminOrOwner(Long namespaceId, String userId) {
        NamespaceRole role = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole)
                .orElseThrow(() -> new DomainForbiddenException("error.namespace.membership.required"));
        if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
    }

    void assertMutable(Namespace namespace) {
        if (namespaceAccessPolicy.isImmutable(namespace)) {
            throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
        }
        if (!namespaceAccessPolicy.canMutateSettings(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }
    }
}
