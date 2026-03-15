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
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.AdminUserSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminUserAppServiceTest {

    private final AdminUserSearchRepository adminUserSearchRepository = mock(AdminUserSearchRepository.class);
    private final UserRoleBindingRepository userRoleBindingRepository = mock(UserRoleBindingRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AdminUserAppService service = new AdminUserAppService(
            adminUserSearchRepository,
            userAccountRepository,
            userRoleBindingRepository,
            roleRepository
    );

    @Test
    void listUsers_returnsPagedUsersFromRepository() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(adminUserSearchRepository.search("ali", UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1")))
                .thenReturn(List.of(new UserRoleBinding("user-1", role("AUDITOR"))));

        PageResponse<?> response = service.listUsers("ali", "ACTIVE", 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0)).extracting("id", "username", "email", "status")
                .containsExactly("user-1", "alice", "alice@example.com", "ACTIVE");
        assertThat(response.items().get(0)).extracting("platformRoles")
                .isEqualTo(List.of("AUDITOR"));
    }

    @Test
    void listUsers_defaultsToUserRoleWhenNoExplicitBindingExists() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(adminUserSearchRepository.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1"))).thenReturn(List.of());

        PageResponse<?> response = service.listUsers(null, null, 0, 20);

        assertThat(response.items().get(0)).extracting("platformRoles")
                .isEqualTo(List.of("USER"));
    }

    @Test
    void listUsers_withInvalidStatus_throwsBadRequest() {
        assertThrows(DomainBadRequestException.class, () -> service.listUsers(null, "BANNED", 0, 20));
    }

    @Test
    void updateUserRole_nonSuperAdminCannotAssignSuperAdmin() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUserRole("user-1", "SUPER_ADMIN", Set.of("USER_ADMIN")));
    }

    @Test
    void updateUserRole_replacesExistingBindings() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));
        when(roleRepository.findByCode("AUDITOR")).thenReturn(Optional.of(role("AUDITOR")));

        var response = service.updateUserRole("user-1", "AUDITOR", Set.of("SUPER_ADMIN"));

        verify(userRoleBindingRepository).deleteByUserId("user-1");
        verify(userRoleBindingRepository).save(any(UserRoleBinding.class));
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.role()).isEqualTo("AUDITOR");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateUserRole_userPseudoRoleClearsBindingsWithoutSavingNewRole() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        var response = service.updateUserRole("user-1", "USER", Set.of("SUPER_ADMIN"));

        verify(userRoleBindingRepository).deleteByUserId("user-1");
        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void updateUserStatus_rejectsUnsupportedStatuses() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        assertThrows(DomainBadRequestException.class, () -> service.updateUserStatus("user-1", "MERGED"));
    }

    @Test
    void updateUserStatus_updatesPersistedStatus() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(user)).thenReturn(user);

        var response = service.updateUserStatus("user-1", "DISABLED");

        verify(userAccountRepository).save(user);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    void updateUserStatus_withUnknownUser_throwsNotFound() {
        when(userAccountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.updateUserStatus("missing", "DISABLED"));
    }

    private UserAccount user(String id, String displayName, String email, UserStatus status) {
        UserAccount user = new UserAccount(id, displayName, email, null);
        user.setStatus(status);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 3, 13, 9, 0));
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.of(2026, 3, 13, 9, 0));
        return user;
    }

    private Role role(String code) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "name", code);
        return role;
    }
}
