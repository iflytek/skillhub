package com.iflytek.skillhub.auth.identity;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IdentityBindingService {

    private final IdentityBindingRepository bindingRepo;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;

    public IdentityBindingService(IdentityBindingRepository bindingRepo,
                                   UserAccountRepository userRepo,
                                   UserRoleBindingRepository roleBindingRepo) {
        this.bindingRepo = bindingRepo;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    @Transactional
    public PlatformPrincipal bindOrCreate(OAuthClaims claims, UserStatus initialStatus) {
        IdentityBinding binding = bindingRepo
            .findByProviderCodeAndSubject(claims.provider(), claims.subject())
            .orElse(null);

        UserAccount user;
        if (binding != null) {
            user = userRepo.findById(binding.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for binding"));
            user.setDisplayName(claims.providerLogin());
            if (claims.email() != null) user.setEmail(claims.email());
            if (claims.extra().get("avatar_url") != null) {
                user.setAvatarUrl((String) claims.extra().get("avatar_url"));
            }
            user = userRepo.save(user);
        } else {
            user = new UserAccount(
                claims.providerLogin(),
                claims.email(),
                (String) claims.extra().get("avatar_url")
            );
            user.setStatus(initialStatus);
            user = userRepo.save(user);

            binding = new IdentityBinding(user.getId(), claims.provider(), claims.subject(), claims.providerLogin());
            bindingRepo.save(binding);
        }

        Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
            .map(rb -> rb.getRole().getCode())
            .collect(Collectors.toSet());

        return new PlatformPrincipal(
            user.getId(), user.getDisplayName(), user.getEmail(),
            user.getAvatarUrl(), claims.provider(), roles
        );
    }
}
