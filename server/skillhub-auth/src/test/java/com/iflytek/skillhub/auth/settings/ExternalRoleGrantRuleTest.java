package com.iflytek.skillhub.auth.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.entity.Role;
import org.junit.jupiter.api.Test;

class ExternalRoleGrantRuleTest {
    @Test
    void consumeRequiresBothIdentityValuesBeforeChangingState() {
        ExternalRoleGrantRule rule = new ExternalRoleGrantRule(
                "github", "admin@example.com", mock(Role.class), "admin");

        assertThatThrownBy(() -> rule.consume(" ", "usr_1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rule.consume("external_1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rule.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.ACTIVE);
        assertThat(rule.getMatchedSubject()).isNull();
        assertThat(rule.getGrantedUserId()).isNull();
        assertThat(rule.getGrantedAt()).isNull();

        rule.consume("external_1", "usr_1");
        assertThat(rule.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.CONSUMED);
        assertThat(rule.getMatchedSubject()).isEqualTo("external_1");
        assertThat(rule.getGrantedUserId()).isEqualTo("usr_1");
        assertThat(rule.getGrantedAt()).isNotNull();
    }
}
