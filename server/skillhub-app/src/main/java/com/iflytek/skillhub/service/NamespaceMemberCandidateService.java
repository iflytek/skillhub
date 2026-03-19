package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;

    public NamespaceMemberCandidateService(NamespaceService namespaceService,
                                           NamespaceAccessPolicy namespaceAccessPolicy,
                                           NamespaceMemberRepository namespaceMemberRepository,
                                           UserAccountRepository userAccountRepository) {
        this.namespaceService = namespaceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.namespaceMemberRepository = namespaceMemberRepository;
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

        String keyword = normalizeSearch(search);
        if (keyword == null) {
            return List.of();
        }

        int pageSize = normalizeSize(size);
        Set<String> existingMemberIds = namespaceMemberRepository.findByNamespaceId(namespace.getId(), PageRequest.of(0, 500))
                .stream()
                .map(NamespaceMember::getUserId)
                .collect(Collectors.toSet());

        return userAccountRepository.search(keyword, UserStatus.ACTIVE, PageRequest.of(0, pageSize)).stream()
                .filter(user -> !existingMemberIds.contains(user.getId()))
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
