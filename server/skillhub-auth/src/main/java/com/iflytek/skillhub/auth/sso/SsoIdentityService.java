package com.iflytek.skillhub.auth.sso;

import java.util.Set;
import java.util.UUID;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves SSO identities to platform users, creating or updating bindings and
 * user records as needed.
 */
@Service
public class SsoIdentityService {

    private static final String PROVIDER_CODE = "sso";

    private final IdentityBindingRepository bindingRepo;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;

    public SsoIdentityService(IdentityBindingRepository bindingRepo,
                              UserAccountRepository userRepo,
                              UserRoleBindingRepository roleBindingRepo,
                              GlobalNamespaceMembershipService globalNamespaceMembershipService) {
        this.bindingRepo = bindingRepo;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
    }

    /**
     * Looks up or auto-creates a platform user for the given SSO identity and
     * returns the corresponding {@link PlatformPrincipal}.
     */
    @Transactional
    public PlatformPrincipal resolveOrCreate(SsoUser ssoUser) {
        IdentityBinding binding = bindingRepo
                .findByProviderCodeAndSubject(PROVIDER_CODE, ssoUser.id())
                .orElse(null);

        UserAccount user;
        if (binding != null) {
            user = userRepo.findById(binding.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found for binding"));
            user.setDisplayName(ssoUser.name());
            user = userRepo.save(user);
        } else {
            user = new UserAccount(
                    "usr_" + UUID.randomUUID(),
                    ssoUser.name(),
                    null,
                    null
            );
            user.setStatus(UserStatus.ACTIVE);
            user = userRepo.save(user);

            globalNamespaceMembershipService.ensureMember(user.getId());

            binding = new IdentityBinding(user.getId(), PROVIDER_CODE,
                    ssoUser.id(), ssoUser.account());
            bindingRepo.save(binding);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active: " + user.getStatus());
        }

        Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
                .map(rb -> rb.getRole().getCode())
                .collect(java.util.stream.Collectors.toSet());
        roles = PlatformRoleDefaults.withDefaultUserRole(roles);

        return new PlatformPrincipal(
                user.getId(), user.getDisplayName(), user.getEmail(),
                user.getAvatarUrl(), PROVIDER_CODE, roles
        );
    }
}
