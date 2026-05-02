package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds predictable users, memberships, and admin roles for the local development profile.
 */
@Component
@Profile({"local", "local-h2", "local-mysql"})
@Order(10)
public class LocalDevDataInitializer implements ApplicationRunner {

    public static final String LOCAL_USER_ID = "local-user";
    public static final String LOCAL_ADMIN_ID = "local-admin";
    private static final String DEFAULT_LOCAL_PASSWORD = "ChangeMe!2026";

    private static final Logger log = LoggerFactory.getLogger(LocalDevDataInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final RoleRepository roleRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalDevDataInitializer(UserAccountRepository userAccountRepository,
                                   NamespaceRepository namespaceRepository,
                                   NamespaceMemberRepository namespaceMemberRepository,
                                   LocalCredentialRepository localCredentialRepository,
                                   RoleRepository roleRepository,
                                   UserRoleBindingRepository userRoleBindingRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.roleRepository = roleRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureSystemRole("SUPER_ADMIN", "超级管理员", "拥有所有权限");
        ensureSystemRole("SKILL_ADMIN", "技能管理员", "全局空间审核、提升审核、隐藏/撤回");
        ensureSystemRole("USER_ADMIN", "用户管理员", "准入审批、封禁/解封、角色分配");
        ensureSystemRole("AUDITOR", "审计员", "查看审计日志");

        UserAccount localUser = ensureUser(
                LOCAL_USER_ID,
                "Local Developer",
                "local-user@example.test"
        );
        UserAccount localAdmin = ensureUser(
                LOCAL_ADMIN_ID,
                "Local Admin",
                "local-admin@example.test"
        );

        Namespace globalNamespace = ensureGlobalNamespace();

        ensureMembership(globalNamespace.getId(), localUser.getId(), NamespaceRole.OWNER);
        ensureMembership(globalNamespace.getId(), localAdmin.getId(), NamespaceRole.OWNER);
        ensureRole(localAdmin.getId(), "SUPER_ADMIN");
        ensureCredential(localUser.getId(), "local-user", DEFAULT_LOCAL_PASSWORD);
        ensureCredential(localAdmin.getId(), "admin", DEFAULT_LOCAL_PASSWORD);

        log.info("Local dev accounts ready: {} / {}", LOCAL_USER_ID, LOCAL_ADMIN_ID);
    }

    private UserAccount ensureUser(String userId, String displayName, String email) {
        return userAccountRepository.findById(userId)
                .map(existing -> {
                    existing.setDisplayName(displayName);
                    existing.setEmail(email);
                    existing.setStatus(UserStatus.ACTIVE);
                    return userAccountRepository.save(existing);
                })
                .orElseGet(() -> userAccountRepository.save(
                        new UserAccount(userId, displayName, email, null)
                ));
    }

    private Namespace ensureGlobalNamespace() {
        return namespaceRepository.findBySlug("global")
                .map(existing -> {
                    existing.setStatus(com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE);
                    existing.setType(NamespaceType.GLOBAL);
                    existing.setDisplayName("Global");
                    existing.setDescription("Platform-level public namespace");
                    return namespaceRepository.save(existing);
                })
                .orElseGet(() -> {
                    Namespace namespace = new Namespace("global", "Global", "system");
                    namespace.setType(NamespaceType.GLOBAL);
                    namespace.setDescription("Platform-level public namespace");
                    namespace.setStatus(com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE);
                    return namespaceRepository.save(namespace);
                });
    }

    private void ensureMembership(Long namespaceId, String userId, NamespaceRole role) {
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseGet(() -> new NamespaceMember(namespaceId, userId, role));
        if (member.getRole() != role) {
            member.setRole(role);
        }
        namespaceMemberRepository.save(member);
    }

    private void ensureRole(String userId, String roleCode) {
        boolean exists = userRoleBindingRepository.findByUserId(userId).stream()
                .map(binding -> binding.getRole().getCode())
                .anyMatch(roleCode::equals);
        if (exists) {
            return;
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing built-in role: " + roleCode));
        userRoleBindingRepository.save(new UserRoleBinding(userId, role));
    }

    private void ensureSystemRole(String code, String name, String description) {
        roleRepository.findByCode(code).orElseGet(() -> {
            Role role = new Role();
            trySetRole(role, code, name, description);
            return roleRepository.save(role);
        });
    }

    private void ensureCredential(String userId, String username, String rawPassword) {
        LocalCredential credential = localCredentialRepository.findByUserId(userId)
                .orElseGet(() -> new LocalCredential(userId, username, passwordEncoder.encode(rawPassword)));
        credential.setUserId(userId);
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        localCredentialRepository.save(credential);
    }

    private void trySetRole(Role role, String code, String name, String description) {
        try {
            java.lang.reflect.Field codeField = Role.class.getDeclaredField("code");
            java.lang.reflect.Field nameField = Role.class.getDeclaredField("name");
            java.lang.reflect.Field descriptionField = Role.class.getDeclaredField("description");
            java.lang.reflect.Field systemField = Role.class.getDeclaredField("system");
            codeField.setAccessible(true);
            nameField.setAccessible(true);
            descriptionField.setAccessible(true);
            systemField.setAccessible(true);
            codeField.set(role, code);
            nameField.set(role, name);
            descriptionField.set(role, description);
            systemField.set(role, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize system role: " + code, e);
        }
    }
}
