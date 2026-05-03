package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UassBootstrapAdminRoleServiceTest {

    @Mock
    private UserRoleBindingRepository userRoleBindingRepository;

    @Mock
    private RoleRepository roleRepository;

    private UassProperties properties;
    private UassBootstrapAdminRoleService service;

    @BeforeEach
    void setUp() {
        properties = new UassProperties();
        service = new UassBootstrapAdminRoleService(userRoleBindingRepository, roleRepository, properties);
    }

    @Test
    void applyIfConfigured_returnsOriginalPrincipalWhenNoRoleNeedsToBeAdded() {
        UassProperties.AdminUserConfig adminUser = new UassProperties.AdminUserConfig();
        adminUser.setUssId("U-1");
        adminUser.setRoles(List.of("USER", "USER_ADMIN"));
        properties.setAdminUsers(List.of(adminUser));
        when(userRoleBindingRepository.findByUserId("user-1"))
                .thenReturn(List.of(new UserRoleBinding("user-1", role("USER_ADMIN"))));

        PlatformPrincipal principal = principal(Set.of("USER", "USER_ADMIN"));

        PlatformPrincipal resolved = service.applyIfConfigured("U-1", true, principal);

        assertThat(resolved).isSameAs(principal);
        verify(roleRepository, never()).findByCode(any());
        verify(userRoleBindingRepository, never()).save(any());
    }

    @Test
    void applyIfConfigured_throwsWhenConfiguredRoleDoesNotExist() {
        UassProperties.AdminUserConfig adminUser = new UassProperties.AdminUserConfig();
        adminUser.setUssId("U-2");
        adminUser.setRoles(List.of("AUDITOR"));
        properties.setAdminUsers(List.of(adminUser));
        when(userRoleBindingRepository.findByUserId("user-1")).thenReturn(List.of());
        when(roleRepository.findByCode("AUDITOR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyIfConfigured("U-2", true, principal(Set.of("USER"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing built-in role: AUDITOR");

        verify(userRoleBindingRepository, never()).save(any());
    }

    @Test
    void applyIfConfigured_addsConfiguredRolesAndReturnsExpandedPrincipal() {
        UassProperties.AdminUserConfig adminUser = new UassProperties.AdminUserConfig();
        adminUser.setUssId("U-3");
        adminUser.setRoles(List.of("USER_ADMIN", "AUDITOR"));
        properties.setAdminUsers(List.of(adminUser));
        when(userRoleBindingRepository.findByUserId("user-1")).thenReturn(List.of());
        when(roleRepository.findByCode("USER_ADMIN")).thenReturn(Optional.of(role("USER_ADMIN")));
        when(roleRepository.findByCode("AUDITOR")).thenReturn(Optional.of(role("AUDITOR")));

        PlatformPrincipal resolved = service.applyIfConfigured("U-3", true, principal(Set.of("USER")));

        assertThat(resolved.platformRoles()).containsExactlyInAnyOrder("USER_ADMIN", "AUDITOR");
        verify(userRoleBindingRepository, times(2)).save(any(UserRoleBinding.class));
    }

    private static PlatformPrincipal principal(Set<String> roles) {
        return new PlatformPrincipal("user-1", "User One", "user@example.com", null, "uass", roles);
    }

    private static Role role(String code) {
        Role role = new Role();
        setField(role, "code", code);
        return role;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
