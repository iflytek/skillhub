package com.iflytek.skillhub.auth.settings;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Applies a single configured role while the newly-created OAuth account is still in its transaction. */
@Service
public class InitialExternalRoleGrantService {
    private final ExternalRoleGrantRuleRepository ruleRepository;
    private final UserRoleBindingRepository roleBindingRepository;
    private final AuditLogService auditLogService;

    public InitialExternalRoleGrantService(ExternalRoleGrantRuleRepository ruleRepository,
                                           UserRoleBindingRepository roleBindingRepository,
                                           AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.auditLogService = auditLogService;
    }

    public void grantForNewUser(OAuthClaims claims, String userId) {
        if (!claims.emailVerified() || claims.email() == null || claims.email().isBlank()) {
            return;
        }
        String provider = normalize(claims.provider());
        String email = normalize(claims.email());
        ruleRepository.lockByIdentityAndStatus(provider, email, ExternalRoleGrantRule.Status.ACTIVE)
                .ifPresent(rule -> {
                    roleBindingRepository.save(new UserRoleBinding(userId, rule.getRole()));
                    rule.consume(claims.subject(), userId);
                    ruleRepository.saveAndFlush(rule);
                    auditLogService.record(null, "INITIAL_ROLE_GRANTED", "EXTERNAL_ROLE_GRANT_RULE",
                            rule.getId(), null, null, null,
                            AuditDetail.builder().put("userId", userId).put("provider", provider)
                                    .put("roleCode", rule.getRole().getCode()).build());
                });
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
