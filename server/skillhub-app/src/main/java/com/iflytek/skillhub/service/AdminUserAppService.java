package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.AdminUserSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrative user-management application service built around the main
 * search and mutation use cases exposed by the admin API.
 */
@Service
public class AdminUserAppService {

    private static final Set<UserStatus> MANAGEABLE_STATUSES = Set.of(UserStatus.ACTIVE, UserStatus.DISABLED);

    private final AdminUserSearchRepository adminUserSearchRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final RoleRepository roleRepository;

    public AdminUserAppService(
            AdminUserSearchRepository adminUserSearchRepository,
            UserAccountRepository userAccountRepository,
            UserRoleBindingRepository userRoleBindingRepository,
            RoleRepository roleRepository) {
        this.adminUserSearchRepository = adminUserSearchRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> listUsers(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserAccount> result = adminUserSearchRepository.search(
                search,
                StringUtils.hasText(status) ? parseStatus(status) : null,
                pageable
        );
        Map<String, List<String>> rolesByUserId = loadRolesByUserId(
                result.getContent().stream().map(UserAccount::getId).toList());

        List<AdminUserSummaryResponse> items = result.getContent().stream()
                .map(user -> new AdminUserSummaryResponse(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getStatus().name(),
                        rolesByUserId.getOrDefault(user.getId(), List.of()),
                        user.getCreatedAt()))
                .toList();

        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional
    public AdminUserMutationResponse updateUserRole(String userId, String roleCode, Set<String> actorPlatformRoles) {
        UserAccount user = loadUser(userId);
        String normalizedRoleCode = normalizeRoleCode(roleCode);

        if ("SUPER_ADMIN".equals(normalizedRoleCode)
                && (actorPlatformRoles == null || !actorPlatformRoles.contains("SUPER_ADMIN"))) {
            throw new DomainForbiddenException("error.admin.user.role.superAdmin.assignDenied");
        }

        userRoleBindingRepository.deleteByUserId(user.getId());

        if (!"USER".equals(normalizedRoleCode)) {
            Role role = roleRepository.findByCode(normalizedRoleCode)
                    .orElseThrow(() -> new DomainBadRequestException("error.admin.user.role.invalid", roleCode));
            userRoleBindingRepository.save(new UserRoleBinding(user.getId(), role));
        }

        return new AdminUserMutationResponse(user.getId(), normalizedRoleCode, user.getStatus().name());
    }

    @Transactional
    public AdminUserMutationResponse updateUserStatus(String userId, String status) {
        UserAccount user = loadUser(userId);
        UserStatus nextStatus = parseManageableStatus(status);
        user.setStatus(nextStatus);
        userAccountRepository.save(user);
        return new AdminUserMutationResponse(user.getId(), null, nextStatus.name());
    }

    private UserStatus parseManageableStatus(String status) {
        UserStatus parsedStatus = parseStatus(status);
        if (!MANAGEABLE_STATUSES.contains(parsedStatus)) {
            throw new DomainBadRequestException("error.admin.user.status.unsupported");
        }
        return parsedStatus;
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.admin.user.status.invalid", status);
        }
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new DomainBadRequestException("error.admin.user.role.invalid", roleCode);
        }
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, List<String>> loadRolesByUserId(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> explicitRolesByUserId = userRoleBindingRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleBinding::getUserId,
                        Collectors.mapping(binding -> binding.getRole().getCode(),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        roles -> roles.stream().sorted().toList()))));
        return userIds.stream().collect(Collectors.toMap(
                userId -> userId,
                userId -> withDefaultUserRole(explicitRolesByUserId.getOrDefault(userId, List.of())).stream()
                        .sorted()
                        .toList()
        ));
    }

    private Set<String> withDefaultUserRole(List<String> roles) {
        Set<String> resolvedRoles = new TreeSet<>();
        if (roles != null) {
            resolvedRoles.addAll(roles);
        }
        if (resolvedRoles.isEmpty()) {
            resolvedRoles.add("USER");
        }
        return Set.copyOf(resolvedRoles);
    }

    private UserAccount loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException("error.admin.user.notFound", userId));
    }
}
