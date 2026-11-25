package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Finds candidate users that can be invited into a namespace while excluding
 * existing members and immutable namespaces.
 */
@Service
public class NamespaceMemberCandidateService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final NamespaceService namespaceService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;
    private final UserAccountRepository userAccountRepository;

    public NamespaceMemberCandidateService(NamespaceService namespaceService,
                                           NamespaceAccessPolicy namespaceAccessPolicy,
                                           UserAccountRepository userAccountRepository) {
        this.namespaceService = namespaceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<NamespaceCandidateUserResponse> searchCandidates(String slug, String search, String operatorUserId, int size) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        if (namespaceAccessPolicy.isImmutable(namespace)) {
            throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
        }
        namespaceService.assertAdminOrOwner(namespace.getId(), operatorUserId);
        if (!namespaceAccessPolicy.canManageMembers(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }

        return findCandidates(namespace, search, size);
    }

    @Transactional(readOnly = true)
    public List<NamespaceCandidateUserResponse> searchCandidatesForPlatformAdmin(String slug, String search, int size) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        if (namespaceAccessPolicy.isImmutable(namespace)) {
            throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
        }
        if (!namespaceAccessPolicy.canManageMembers(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }

        return findCandidates(namespace, search, size);
    }

    private List<NamespaceCandidateUserResponse> findCandidates(Namespace namespace, String search, int size) {
        String keyword = normalizeSearch(search);
        if (keyword == null) {
            return List.of();
        }

        int pageSize = normalizeSize(size);
        return userAccountRepository.searchNonMembers(namespace.getId(), keyword, PageRequest.of(0, pageSize)).stream()
                .map(NamespaceCandidateUserResponse::from)
                .toList();
    }

    private String normalizeSearch(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        String keyword = search.trim();
        if (keyword.length() < 2) {
            throw new DomainBadRequestException("error.namespace.member.search.tooShort");
        }
        return keyword;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(size, MAX_LIMIT);
    }
}
