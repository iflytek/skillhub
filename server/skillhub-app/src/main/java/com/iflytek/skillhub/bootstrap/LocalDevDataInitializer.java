package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds predictable users, memberships, and admin roles for the local development profile.
 */
@Component
@Profile("local")
public class LocalDevDataInitializer implements ApplicationRunner {

    public static final String LOCAL_USER_ID = "local-user";
    public static final String LOCAL_ADMIN_ID = "local-admin";

    private static final Logger log = LoggerFactory.getLogger(LocalDevDataInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final RoleRepository roleRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;

    public LocalDevDataInitializer(UserAccountRepository userAccountRepository,
                                   NamespaceRepository namespaceRepository,
                                   NamespaceMemberRepository namespaceMemberRepository,
                                   RoleRepository roleRepository,
                                   UserRoleBindingRepository userRoleBindingRepository) {
        this.userAccountRepository = userAccountRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.roleRepository = roleRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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

        Namespace globalNamespace = namespaceRepository.findBySlug("global")
                .orElseThrow(() -> new IllegalStateException("Missing built-in global namespace"));

        ensureMembership(globalNamespace.getId(), localUser.getId(), NamespaceRole.OWNER);
        ensureMembership(globalNamespace.getId(), localAdmin.getId(), NamespaceRole.OWNER);
        ensureRole(localAdmin.getId(), "SUPER_ADMIN");

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
}
