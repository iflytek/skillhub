package com.iflytek.skillhub.auth.uass;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Maps normalized UASS login/profile payloads onto the existing identity-binding
 * workflow so browser session provisioning can reuse the same local user model.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class UassIdentityService {

    static final String PROVIDER_CODE = "uass";

    private final IdentityBindingService identityBindingService;
    private final UassBootstrapAdminRoleService uassBootstrapAdminRoleService;

    public UassIdentityService(IdentityBindingService identityBindingService,
                               UassBootstrapAdminRoleService uassBootstrapAdminRoleService) {
        this.identityBindingService = identityBindingService;
        this.uassBootstrapAdminRoleService = uassBootstrapAdminRoleService;
    }

    public PlatformPrincipal resolvePrincipal(UassLoginContext loginContext, UassUserProfile userProfile) {
        String userCode = requireUserCode(loginContext, userProfile);
        OAuthClaims claims = new OAuthClaims(
                PROVIDER_CODE,
                userCode,
                normalizeEmail(userProfile.email()),
                true,
                resolveDisplayName(userCode, userProfile.displayName()),
                buildExtra(userProfile)
        );
        IdentityBindingService.BindOrCreateResult result = identityBindingService.bindOrCreateResult(claims, UserStatus.ACTIVE);
        return uassBootstrapAdminRoleService.applyIfConfigured(userCode, result.newlyCreated(), result.principal());
    }

    private static String requireUserCode(UassLoginContext loginContext, UassUserProfile userProfile) {
        String userCode = normalizeOptional(loginContext.userCode());
        if (userCode == null) {
            userCode = normalizeOptional(userProfile.userCode());
        }
        if (userCode == null) {
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.uass.userCodeMissing");
        }
        return userCode;
    }

    private static String resolveDisplayName(String userCode, String displayName) {
        String normalized = normalizeOptional(displayName);
        return normalized == null ? userCode : normalized;
    }

    private static Map<String, Object> buildExtra(UassUserProfile userProfile) {
        Map<String, Object> extra = new HashMap<>();
        String ussId = normalizeOptional(userProfile.userCode());
        if (ussId != null) {
            extra.put("uss_id", ussId);
        }
        String avatarUrl = normalizeOptional(userProfile.attributes().get("avatar_url"));
        if (avatarUrl != null) {
            extra.put("avatar_url", avatarUrl);
        }
        return extra;
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeEmail(String email) {
        String normalized = normalizeOptional(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

}
