package com.iflytek.skillhub.auth.rbac;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock
    private UserRoleBindingRepository roleBindingRepo;

    @Mock
    private EntityManager entityManager;

    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        rbacService = new RbacService(roleBindingRepo, entityManager);
    }

    @Test
    void getUserRoleCodes_shouldReturnDefaultUserRole_whenNoBindings() {
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of());

        Set<String> roles = rbacService.getUserRoleCodes("user-1");

        assertEquals(Set.of("USER"), roles);
    }

    @Test
    void getUserRoleCodes_shouldReturnRoleCodesFromBindings() {
        Role adminRole = createRole(1L, "ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", adminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        Set<String> roles = rbacService.getUserRoleCodes("user-1");

        assertEquals(Set.of("ADMIN"), roles);
    }

    @Test
    void getUserPermissions_shouldReturnEmptySet_whenNoBindings() {
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of());

        Set<String> permissions = rbacService.getUserPermissions("user-1");

        assertTrue(permissions.isEmpty());
    }

    @Test
    void getUserPermissions_shouldReturnAllPermissions_whenSuperAdmin() {
        Role superAdminRole = createRole(1L, "SUPER_ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", superAdminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        when(entityManager.createQuery("SELECT p.code FROM Permission p", String.class)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("perm.read", "perm.write"));

        Set<String> permissions = rbacService.getUserPermissions("user-1");

        assertEquals(Set.of("perm.read", "perm.write"), permissions);
    }

    @Test
    void getUserPermissions_shouldReturnRolePermissions_whenRegularUser() {
        Role adminRole = createRole(1L, "ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", adminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        when(entityManager.createQuery(
                "SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId IN :roleIds",
                String.class)).thenReturn(query);
        when(query.setParameter(eq("roleIds"), any(Set.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("perm.read"));

        Set<String> permissions = rbacService.getUserPermissions("user-1");

        assertEquals(Set.of("perm.read"), permissions);
    }

    @Test
    void hasPermission_shouldReturnTrue_whenUserHasPermission() {
        Role adminRole = createRole(1L, "ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", adminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        when(entityManager.createQuery(
                "SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId IN :roleIds",
                String.class)).thenReturn(query);
        when(query.setParameter(eq("roleIds"), any(Set.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("perm.read"));

        assertTrue(rbacService.hasPermission("user-1", "perm.read"));
    }

    @Test
    void hasPermission_shouldReturnFalse_whenUserLacksPermission() {
        Role adminRole = createRole(1L, "ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", adminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        when(entityManager.createQuery(
                "SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId IN :roleIds",
                String.class)).thenReturn(query);
        when(query.setParameter(eq("roleIds"), any(Set.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("perm.read"));

        assertFalse(rbacService.hasPermission("user-1", "perm.write"));
    }

    @Test
    void hasRole_shouldReturnTrue_whenUserHasRole() {
        Role adminRole = createRole(1L, "ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-1", adminRole);
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of(binding));

        assertTrue(rbacService.hasRole("user-1", "ADMIN"));
    }

    @Test
    void hasRole_shouldReturnFalse_whenUserLacksRole() {
        when(roleBindingRepo.findByUserId("user-1")).thenReturn(List.of());

        assertFalse(rbacService.hasRole("user-1", "ADMIN"));
    }

    private Role createRole(Long id, String code) {
        Role role = new Role();
        try {
            java.lang.reflect.Field idField = Role.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(role, id);
            java.lang.reflect.Field codeField = Role.class.getDeclaredField("code");
            codeField.setAccessible(true);
            codeField.set(role, code);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return role;
    }
}
