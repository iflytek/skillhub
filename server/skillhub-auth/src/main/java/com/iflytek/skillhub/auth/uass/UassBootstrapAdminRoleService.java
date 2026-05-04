package com.iflytek.skillhub.auth.uass;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Applies one-time bootstrap admin roles for newly-created UASS users.
 *
 * <p>The YAML-configured admin list is only used to seed brand new accounts
 * during rollout. Day-to-day role changes should continue through the admin UI.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class UassBootstrapAdminRoleService {

    private static final String BOOTSTRAP_ADMIN_ROLE = "SUPER_ADMIN";

    private final UserRoleBindingRepository userRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final UassProperties uassProperties;

    public UassBootstrapAdminRoleService(UserRoleBindingRepository userRoleBindingRepository,
                                         RoleRepository roleRepository,
                                         UassProperties uassProperties) {
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.uassProperties = uassProperties;
    }

    public PlatformPrincipal applyIfConfigured(String ussId, boolean newlyCreated, PlatformPrincipal principal) {
        if (!newlyCreated) {
            return principal;
        }

        if (!uassProperties.isBootstrapAdminUssId(ussId)) {
            return principal;
        }

        List<UserRoleBinding> existingBindings = userRoleBindingRepository.findByUserId(principal.userId());
        Set<String> existingRoleCodes = existingBindings.stream()
                .map(binding -> binding.getRole().getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (existingRoleCodes.contains(BOOTSTRAP_ADMIN_ROLE)) {
            return principal;
        }

        Role role = roleRepository.findByCode(BOOTSTRAP_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Missing built-in role: " + BOOTSTRAP_ADMIN_ROLE));
        userRoleBindingRepository.save(new UserRoleBinding(principal.userId(), role));
        existingRoleCodes.add(BOOTSTRAP_ADMIN_ROLE);

        Set<String> resolvedRoles = PlatformRoleDefaults.withDefaultUserRole(existingRoleCodes);
        return new PlatformPrincipal(
                principal.userId(),
                principal.displayName(),
                principal.email(),
                principal.avatarUrl(),
                principal.oauthProvider(),
                resolvedRoles
        );
    }
}
