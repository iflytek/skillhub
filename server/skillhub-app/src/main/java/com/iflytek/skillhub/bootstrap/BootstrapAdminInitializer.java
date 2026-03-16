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
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default bootstrap admin account for any runtime profile.
 * Idempotent: skips if admin credential already exists.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties bootstrapAdminProperties;
    private final UserAccountRepository userAccountRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final RoleRepository roleRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties bootstrapAdminProperties,
                                     UserAccountRepository userAccountRepository,
                                     LocalCredentialRepository localCredentialRepository,
                                     RoleRepository roleRepository,
                                     UserRoleBindingRepository userRoleBindingRepository,
                                     NamespaceRepository namespaceRepository,
                                     NamespaceMemberRepository namespaceMemberRepository,
                                     PasswordEncoder passwordEncoder) {
        this.bootstrapAdminProperties = bootstrapAdminProperties;
        this.userAccountRepository = userAccountRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.roleRepository = roleRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapAdminProperties.isEnabled()) {
            log.info("Bootstrap admin is disabled");
            return;
        }
        if (localCredentialRepository.existsByUsernameIgnoreCase(bootstrapAdminProperties.getUsername())) {
            log.info("Bootstrap admin already exists, skipping");
            return;
        }

        // 1. Create admin user account
        UserAccount admin = userAccountRepository.findById(bootstrapAdminProperties.getUserId())
                .orElseGet(() -> userAccountRepository.save(
                        new UserAccount(
                                bootstrapAdminProperties.getUserId(),
                                bootstrapAdminProperties.getDisplayName(),
                                bootstrapAdminProperties.getEmail(),
                                null
                        )
                ));
        admin.setDisplayName(bootstrapAdminProperties.getDisplayName());
        admin.setEmail(bootstrapAdminProperties.getEmail());
        admin = userAccountRepository.save(admin);

        // 2. Create local credential (username/password)
        localCredentialRepository.save(
                new LocalCredential(
                        admin.getId(),
                        bootstrapAdminProperties.getUsername(),
                        passwordEncoder.encode(bootstrapAdminProperties.getPassword())
                )
        );

        // 3. Assign SUPER_ADMIN role
        Role superAdmin = roleRepository.findByCode("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Missing built-in role: SUPER_ADMIN"));
        boolean hasRole = userRoleBindingRepository.findByUserId(admin.getId()).stream()
                .anyMatch(b -> b.getRole().getCode().equals("SUPER_ADMIN"));
        if (!hasRole) {
            userRoleBindingRepository.save(new UserRoleBinding(admin.getId(), superAdmin));
        }

        // 4. Ensure global namespace + membership
        Namespace globalNs = namespaceRepository.findBySlug("global")
                .orElseThrow(() -> new IllegalStateException("Missing built-in global namespace"));
        if (namespaceMemberRepository.findByNamespaceIdAndUserId(globalNs.getId(), admin.getId()).isEmpty()) {
            namespaceMemberRepository.save(new NamespaceMember(globalNs.getId(), admin.getId(), NamespaceRole.OWNER));
        }

        log.info("Bootstrap admin initialized for account: {}", bootstrapAdminProperties.getUsername());
    }
}
