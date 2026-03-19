package com.iflytek.skillhub.auth.rbac;

import com.iflytek.skillhub.auth.entity.Permission;
import com.iflytek.skillhub.auth.entity.RolePermission;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves platform roles and permissions for a user from persisted RBAC
 * bindings.
 */
@Service
public class RbacService {

    private final UserRoleBindingRepository roleBindingRepo;
    private final EntityManager entityManager;

    public RbacService(UserRoleBindingRepository roleBindingRepo, EntityManager entityManager) {
        this.roleBindingRepo = roleBindingRepo;
        this.entityManager = entityManager;
    }

    public Set<String> getUserRoleCodes(String userId) {
        return PlatformRoleDefaults.withDefaultUserRole(roleBindingRepo.findByUserId(userId).stream()
            .map(rb -> rb.getRole().getCode())
            .collect(Collectors.toSet()));
    }

    public Set<String> getUserPermissions(String userId) {
        List<UserRoleBinding> bindings = roleBindingRepo.findByUserId(userId);
        Set<Long> roleIds = bindings.stream()
            .map(rb -> rb.getRole().getId())
            .collect(Collectors.toSet());

        if (roleIds.isEmpty()) return Set.of();

        // Check if user has SUPER_ADMIN role - grant all permissions
        boolean isSuperAdmin = bindings.stream()
            .anyMatch(rb -> "SUPER_ADMIN".equals(rb.getRole().getCode()));
        if (isSuperAdmin) {
            return entityManager.createQuery("SELECT p.code FROM Permission p", String.class)
                .getResultList().stream().collect(Collectors.toSet());
        }

        return entityManager.createQuery(
                "SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId IN :roleIds", String.class)
            .setParameter("roleIds", roleIds)
            .getResultList().stream().collect(Collectors.toSet());
    }

    public boolean hasPermission(String userId, String permissionCode) {
        return getUserPermissions(userId).contains(permissionCode);
    }

    public boolean hasRole(String userId, String roleCode) {
        return getUserRoleCodes(userId).contains(roleCode);
    }
}
