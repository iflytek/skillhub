package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MyNamespaceResponse;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query-facing namespace application service that keeps controller methods
 * thin while preserving current response contracts.
 */
@Service
public class NamespacePortalQueryAppService {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String NAMESPACE_SLUG_SORT = "slug";
    private static final int DEFAULT_MY_NAMESPACE_PAGE_SIZE = 20;
    private static final int MAX_MY_NAMESPACE_PAGE_SIZE = 100;

    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;
    private final UserAccountRepository userAccountRepository;

    public NamespacePortalQueryAppService(NamespaceRepository namespaceRepository,
                                          NamespaceService namespaceService,
                                          NamespaceMemberService namespaceMemberService,
                                          NamespaceAccessPolicy namespaceAccessPolicy,
                                          UserAccountRepository userAccountRepository) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NamespaceResponse> listNamespaces(Pageable pageable, Map<Long, NamespaceRole> userNamespaceRoles) {
        return listNamespaces(pageable, userNamespaceRoles, Set.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<NamespaceResponse> listNamespaces(Pageable pageable,
                                                          Map<Long, NamespaceRole> userNamespaceRoles,
                                                          Set<String> platformRoles) {
        if (isSuperAdmin(platformRoles)) {
            Page<Namespace> namespaces = namespaceRepository.findByStatus(
                    NamespaceStatus.ACTIVE,
                    PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            Sort.by(NAMESPACE_SLUG_SORT).ascending()
                    )
            );
            return PageResponse.from(namespaces.map(NamespaceResponse::from));
        }

        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (namespaceRoles.isEmpty()) {
            Page<NamespaceResponse> empty = new PageImpl<>(
                    List.of(),
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                    0
            );
            return PageResponse.from(empty);
        }

        List<Namespace> scopedNamespaces = namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .sorted(Comparator.comparing(Namespace::getSlug))
                .toList();
        int fromIndex = Math.min((int) pageable.getOffset(), scopedNamespaces.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), scopedNamespaces.size());
        Page<NamespaceResponse> page = new PageImpl<>(
                scopedNamespaces.subList(fromIndex, toIndex).stream()
                        .map(NamespaceResponse::from)
                        .toList(),
                pageable,
                scopedNamespaces.size()
        );
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public List<MyNamespaceResponse> listMyNamespaces(Map<Long, NamespaceRole> userNamespaceRoles) {
        return listMyNamespaces(userNamespaceRoles, Set.of());
    }

    @Transactional(readOnly = true)
    public List<MyNamespaceResponse> listMyNamespaces(Map<Long, NamespaceRole> userNamespaceRoles,
                                                      Set<String> platformRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (namespaceRoles.isEmpty() && !isSuperAdmin(platformRoles)) {
            return List.of();
        }

        List<Namespace> visibleNamespaces = isSuperAdmin(platformRoles)
                ? listAllNamespacesByPage()
                : namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                        .sorted(Comparator.comparing(Namespace::getSlug))
                        .toList();

        return visibleNamespaces.stream()
                .map(namespace -> myNamespaceResponse(namespace, namespaceRoles))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MyNamespaceResponse> listMyNamespaces(Pageable pageable,
                                                              Map<Long, NamespaceRole> userNamespaceRoles,
                                                              Set<String> platformRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        Pageable boundedPageable = normalizeMyNamespacesPageable(pageable);
        if (namespaceRoles.isEmpty() && !isSuperAdmin(platformRoles)) {
            Page<MyNamespaceResponse> empty = new PageImpl<>(List.of(), boundedPageable, 0);
            return PageResponse.from(empty);
        }

        if (isSuperAdmin(platformRoles)) {
            Page<Namespace> visibleNamespaces = namespaceRepository.findAll(boundedPageable);
            return PageResponse.from(visibleNamespaces.map(namespace -> myNamespaceResponse(namespace, namespaceRoles)));
        }

        List<Namespace> visibleNamespaces = namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                .sorted(Comparator.comparing(Namespace::getSlug))
                .toList();
        int fromIndex = Math.min((int) boundedPageable.getOffset(), visibleNamespaces.size());
        int toIndex = Math.min(fromIndex + boundedPageable.getPageSize(), visibleNamespaces.size());
        Page<MyNamespaceResponse> responsePage = new PageImpl<>(
                visibleNamespaces.subList(fromIndex, toIndex).stream()
                        .map(namespace -> myNamespaceResponse(namespace, namespaceRoles))
                        .toList(),
                boundedPageable,
                visibleNamespaces.size()
        );
        return PageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyNamespaceResponse> listMyNamespaces(Pageable pageable,
                                                              Map<Long, NamespaceRole> userNamespaceRoles,
                                                              Set<String> platformRoles,
                                                              NamespaceStatus status,
                                                              NamespaceType type,
                                                              String query,
                                                              String slug,
                                                              Set<NamespaceRole> roles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        Set<NamespaceRole> requestedRoles = roles != null ? roles : Set.of();
        Pageable boundedPageable = normalizeMyNamespacesPageable(pageable);
        String normalizedQuery = normalizeSearchFilter(query);
        String normalizedSlug = normalizeFilter(slug);

        if (isSuperAdmin(platformRoles) && requestedRoles.isEmpty()) {
            Page<Namespace> visibleNamespaces = namespaceRepository.search(
                    status,
                    type,
                    normalizedQuery,
                    normalizedSlug,
                    boundedPageable
            );
            return PageResponse.from(visibleNamespaces.map(namespace -> myNamespaceResponse(namespace, namespaceRoles)));
        }

        List<Long> scopedNamespaceIds = namespaceRoles.entrySet().stream()
                .filter(entry -> requestedRoles.isEmpty() || requestedRoles.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (scopedNamespaceIds.isEmpty()) {
            Page<MyNamespaceResponse> empty = new PageImpl<>(List.of(), boundedPageable, 0);
            return PageResponse.from(empty);
        }

        Page<Namespace> visibleNamespaces = namespaceRepository.searchByIdIn(
                scopedNamespaceIds,
                status,
                type,
                normalizedQuery,
                normalizedSlug,
                boundedPageable
        );
        return PageResponse.from(visibleNamespaces.map(namespace -> myNamespaceResponse(namespace, namespaceRoles)));
    }

    @Transactional(readOnly = true)
    public NamespaceResponse getNamespace(String slug, String userId, Map<Long, NamespaceRole> userNamespaceRoles) {
        return getNamespace(slug, userId, userNamespaceRoles, Set.of());
    }

    @Transactional(readOnly = true)
    public NamespaceResponse getNamespace(String slug,
                                          String userId,
                                          Map<Long, NamespaceRole> userNamespaceRoles,
                                          Set<String> platformRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        boolean superAdmin = isSuperAdmin(platformRoles);
        Namespace namespace = superAdmin
                ? namespaceService.getNamespaceBySlug(slug)
                : namespaceService.getNamespaceBySlugForRead(
                        slug,
                        userId,
                        namespaceRoles);
        if (!superAdmin && !namespaceRoles.containsKey(namespace.getId())) {
            throw new DomainForbiddenException("error.namespace.membership.required");
        }
        return NamespaceResponse.from(namespace);
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(String slug, Pageable pageable, String userId, Set<String> platformRoles) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        if (namespace.getType() == NamespaceType.GLOBAL) {
            Set<String> roles = platformRoles != null ? platformRoles : Set.of();
            if (!roles.contains("SUPER_ADMIN") && !roles.contains("USER_ADMIN")) {
                throw new DomainForbiddenException("error.namespace.global.members.platformAdmin.required");
            }
        } else {
            namespaceService.assertMember(namespace.getId(), userId);
        }
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);

        List<String> memberUserIds = members.getContent().stream()
                .map(NamespaceMember::getUserId)
                .toList();

        Map<String, UserAccount> userMap = memberUserIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(memberUserIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        return PageResponse.from(members.map(member ->
                MemberResponse.from(member, userMap.get(member.getUserId()))
        ));
    }

    private MyNamespaceResponse myNamespaceResponse(Namespace namespace, Map<Long, NamespaceRole> namespaceRoles) {
        NamespaceRole currentUserRole = namespaceRoles.get(namespace.getId());
        return MyNamespaceResponse.from(
                namespace,
                currentUserRole,
                namespaceAccessPolicy,
                namespaceService.canDelete(namespace, currentUserRole));
    }

    private List<Namespace> listAllNamespacesByPage() {
        List<Namespace> namespaces = new ArrayList<>();
        int pageNumber = 0;
        Page<Namespace> page;
        do {
            page = namespaceRepository.findAll(PageRequest.of(
                    pageNumber,
                    MAX_MY_NAMESPACE_PAGE_SIZE,
                    Sort.by(NAMESPACE_SLUG_SORT).ascending()
            ));
            namespaces.addAll(page.getContent());
            pageNumber++;
            if (page.getContent().isEmpty()) {
                break;
            }
        } while (!page.isLast() && namespaces.size() < page.getTotalElements());
        return namespaces;
    }

    private Pageable normalizeMyNamespacesPageable(Pageable pageable) {
        int page = pageable != null && pageable.isPaged()
                ? Math.max(pageable.getPageNumber(), 0)
                : 0;
        int requestedSize = pageable != null && pageable.isPaged()
                ? pageable.getPageSize()
                : DEFAULT_MY_NAMESPACE_PAGE_SIZE;
        int size = Math.min(Math.max(requestedSize, 1), MAX_MY_NAMESPACE_PAGE_SIZE);
        return PageRequest.of(page, size, normalizeMyNamespacesSort(pageable));
    }

    private Sort normalizeMyNamespacesSort(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return Sort.by(NAMESPACE_SLUG_SORT).ascending();
        }
        Sort.Order slugOrder = pageable.getSort().getOrderFor(NAMESPACE_SLUG_SORT);
        if (slugOrder == null) {
            return Sort.by(NAMESPACE_SLUG_SORT).ascending();
        }
        return Sort.by(new Sort.Order(slugOrder.getDirection(), NAMESPACE_SLUG_SORT));
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSearchFilter(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains(SUPER_ADMIN_ROLE);
    }
}
