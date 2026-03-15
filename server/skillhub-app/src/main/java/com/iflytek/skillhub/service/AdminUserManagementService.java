package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
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
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserManagementService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final RoleRepository roleRepository;

    public AdminUserManagementService(UserAccountRepository userAccountRepository,
                                      UserRoleBindingRepository userRoleBindingRepository,
                                      RoleRepository roleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> listUsers(String keyword, String status, int page, int size) {
        UserStatus userStatus = parseStatus(status);
        Page<UserAccount> users = userAccountRepository.search(normalize(keyword), userStatus, PageRequest.of(page, size));
        List<AdminUserSummaryResponse> items = users.getContent().stream()
            .map(this::toSummary)
            .toList();
        return PageResponse.from(new PageImpl<>(items, users.getPageable(), users.getTotalElements()));
    }

    @Transactional
    public AdminUserSummaryResponse updateUserRole(String userId, String roleCode, PlatformPrincipal principal) {
        UserAccount user = loadUser(userId);
        if (principal != null
                && !principal.platformRoles().contains("SUPER_ADMIN")
                && "SUPER_ADMIN".equalsIgnoreCase(roleCode)) {
            throw new DomainForbiddenException("error.admin.role.assign_super_admin_forbidden");
        }
        Role role = roleRepository.findByCode(roleCode)
            .orElseThrow(() -> new DomainBadRequestException("error.role.notFound", roleCode));

        List<UserRoleBinding> existing = userRoleBindingRepository.findByUserId(userId);
        boolean alreadyAssigned = existing.stream().anyMatch(binding -> binding.getRole().getCode().equals(roleCode));
        if (!alreadyAssigned) {
            userRoleBindingRepository.save(new UserRoleBinding(userId, role));
        }
        return toSummary(user);
    }

    @Transactional
    public AdminUserSummaryResponse approveUser(String userId) {
        UserAccount user = loadUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        return toSummary(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryResponse updateUserStatus(String userId, String status) {
        UserAccount user = loadUser(userId);
        user.setStatus(parseRequiredStatus(status));
        return toSummary(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryResponse disableUser(String userId) {
        UserAccount user = loadUser(userId);
        user.setStatus(UserStatus.DISABLED);
        return toSummary(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryResponse enableUser(String userId) {
        UserAccount user = loadUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        return toSummary(userAccountRepository.save(user));
    }

    private UserAccount loadUser(String userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new DomainNotFoundException("error.user.notFound", userId));
    }

    private AdminUserSummaryResponse toSummary(UserAccount user) {
        Set<String> roles = new LinkedHashSet<>();
        userRoleBindingRepository.findByUserId(user.getId()).stream()
            .map(binding -> binding.getRole().getCode())
            .sorted(Comparator.naturalOrder())
            .forEach(roles::add);
        roles = new LinkedHashSet<>(PlatformRoleDefaults.withDefaultUserRole(roles));
        return new AdminUserSummaryResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getStatus().name(),
            List.copyOf(roles),
            user.getCreatedAt()
        );
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return parseRequiredStatus(status);
    }

    private UserStatus parseRequiredStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.user.status.invalid", status);
        }
    }
}
