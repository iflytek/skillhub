package com.iflytek.skillhub.auth.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitialExternalRoleGrantServiceTest {
    @Mock private ExternalRoleGrantRuleRepository rules;
    @Mock private UserRoleBindingRepository bindings;
    @Mock private AuditLogService audit;
    @Mock private Role role;
    private InitialExternalRoleGrantService service;

    @BeforeEach
    void setUp() {
        service = new InitialExternalRoleGrantService(rules, bindings, audit);
    }

    @Test
    void verifiedEmailConsumesMatchingRuleAndGrantsNewUser() {
        ExternalRoleGrantRule rule = new ExternalRoleGrantRule("feishu", "admin@example.com", role, null);
        when(rules.lockByIdentityAndStatus("feishu", "admin@example.com", ExternalRoleGrantRule.Status.ACTIVE))
                .thenReturn(Optional.of(rule));
        when(role.getCode()).thenReturn("SUPER_ADMIN");

        service.grantForNewUser(new OAuthClaims("FEISHU", "external-42", " Admin@Example.COM ", true,
                "admin", Map.of()), "usr_new");

        ArgumentCaptor<UserRoleBinding> grant = ArgumentCaptor.forClass(UserRoleBinding.class);
        verify(bindings).save(grant.capture());
        assertThat(grant.getValue().getUserId()).isEqualTo("usr_new");
        assertThat(grant.getValue().getRole()).isSameAs(role);
        assertThat(rule.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.CONSUMED);
        assertThat(rule.getMatchedSubject()).isEqualTo("external-42");
        assertThat(rule.getGrantedUserId()).isEqualTo("usr_new");
        verify(rules).saveAndFlush(rule);
        verify(audit).record(eq(null), eq("INITIAL_ROLE_GRANTED"), eq("EXTERNAL_ROLE_GRANT_RULE"),
                eq(rule.getId()), eq(null), eq(null), eq(null), any());
    }

    @Test
    void unverifiedEmailCannotConsumeRule() {
        service.grantForNewUser(new OAuthClaims("feishu", "external-42", "admin@example.com", false,
                "admin", Map.of()), "usr_new");

        verify(rules, never()).lockByIdentityAndStatus(any(), any(), eq(ExternalRoleGrantRule.Status.ACTIVE));
        verify(bindings, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void absentActiveRuleDoesNotGrantOrAudit() {
        when(rules.lockByIdentityAndStatus("feishu", "admin@example.com", ExternalRoleGrantRule.Status.ACTIVE))
                .thenReturn(Optional.empty());

        service.grantForNewUser(new OAuthClaims("feishu", "external-42", "admin@example.com", true,
                "admin", Map.of()), "usr_new");

        verify(bindings, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
