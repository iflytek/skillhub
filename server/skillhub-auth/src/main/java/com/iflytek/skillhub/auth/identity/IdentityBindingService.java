package com.iflytek.skillhub.auth.identity;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves external OAuth identities to platform users, creating or updating
 * bindings and user records as needed.
 */
@Service
public class IdentityBindingService {

    private final IdentityBindingRepository bindingRepo;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;

    public IdentityBindingService(IdentityBindingRepository bindingRepo,
                                  UserAccountRepository userRepo,
                                  UserRoleBindingRepository roleBindingRepo,
                                  GlobalNamespaceMembershipService globalNamespaceMembershipService) {
        this.bindingRepo = bindingRepo;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
    }

    @Transactional
    public PlatformPrincipal bindOrCreate(OAuthClaims claims, UserStatus initialStatus) {
        return bindOrCreateResult(claims, initialStatus).principal();
    }

    @Transactional
    public BindOrCreateResult bindOrCreateResult(OAuthClaims claims, UserStatus initialStatus) {
        IdentityBinding binding = bindingRepo
            .findByProviderCodeAndSubject(claims.provider(), claims.subject())
            .orElse(null);

        boolean newlyCreated = false;
        UserAccount user;
        if (binding != null) {
            user = userRepo.findById(binding.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for binding"));
            refreshUserFromClaims(user, claims);
            user = userRepo.save(user);
        } else {
            boolean[] createdNewUser = {false};
            user = resolveExistingUser(claims)
                .map(existingUser -> {
                    refreshUserFromClaims(existingUser, claims);
                    return existingUser;
                })
                .orElseGet(() -> {
                    createdNewUser[0] = true;
                    return newUserFromClaims(claims, initialStatus);
                });
            newlyCreated = createdNewUser[0];
            user = userRepo.save(user);
            if (newlyCreated && initialStatus == UserStatus.ACTIVE) {
                globalNamespaceMembershipService.ensureMember(user.getId());
            }

            binding = new IdentityBinding(user.getId(), claims.provider(), claims.subject(), claims.providerLogin());
            bindingRepo.save(binding);
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new com.iflytek.skillhub.auth.oauth.AccountPendingException();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new com.iflytek.skillhub.auth.oauth.AccountDisabledException();
        }

        Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
            .map(rb -> rb.getRole().getCode())
            .collect(Collectors.toSet());
        roles = PlatformRoleDefaults.withDefaultUserRole(roles);

        PlatformPrincipal principal = new PlatformPrincipal(
            user.getId(), user.getDisplayName(), user.getEmail(),
            user.getAvatarUrl(), claims.provider(), roles
        );
        return new BindOrCreateResult(principal, newlyCreated);
    }

    @Transactional
    public void createPendingUserIfAbsent(OAuthClaims claims) {
        IdentityBinding existingBinding = bindingRepo
            .findByProviderCodeAndSubject(claims.provider(), claims.subject())
            .orElse(null);
        if (existingBinding != null) {
            UserAccount existingUser = userRepo.findById(existingBinding.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for binding"));
            if (existingUser.getStatus() == UserStatus.DISABLED) {
                throw new com.iflytek.skillhub.auth.oauth.AccountDisabledException();
            }
            throw new com.iflytek.skillhub.auth.oauth.AccountPendingException();
        }

        UserAccount user = newUserFromClaims(claims, UserStatus.PENDING);
        user = userRepo.save(user);

        IdentityBinding binding = new IdentityBinding(user.getId(), claims.provider(), claims.subject(), claims.providerLogin());
        bindingRepo.save(binding);
    }

    private Optional<UserAccount> resolveExistingUser(OAuthClaims claims) {
        String ussId = normalizeOptional(extraValue(claims.extra(), "uss_id"));
        if (!"uass".equalsIgnoreCase(claims.provider()) || ussId == null) {
            return Optional.empty();
        }
        return userRepo.findByUssId(ussId);
    }

    private UserAccount newUserFromClaims(OAuthClaims claims, UserStatus initialStatus) {
        UserAccount user = new UserAccount(
            "usr_" + UUID.randomUUID(),
            claims.providerLogin(),
            claims.email(),
            extraValue(claims.extra(), "avatar_url")
        );
        user.setStatus(initialStatus);
        user.setUssId(normalizeOptional(extraValue(claims.extra(), "uss_id")));
        return user;
    }

    private void refreshUserFromClaims(UserAccount user, OAuthClaims claims) {
        user.setDisplayName(claims.providerLogin());
        if (claims.email() != null) {
            user.setEmail(claims.email());
        }
        String avatarUrl = extraValue(claims.extra(), "avatar_url");
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        String ussId = normalizeOptional(extraValue(claims.extra(), "uss_id"));
        if (ussId != null) {
            user.setUssId(ussId);
        }
    }

    private static String extraValue(Map<String, Object> extra, String key) {
        Object value = extra.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record BindOrCreateResult(PlatformPrincipal principal, boolean newlyCreated) {
    }
}
