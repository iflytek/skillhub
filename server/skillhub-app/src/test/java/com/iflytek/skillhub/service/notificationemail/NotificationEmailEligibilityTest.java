package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.social.*;
import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.listener.RecipientResolver;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailItem;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationEmailEligibilityTest {
    SkillRepository skills = mock(SkillRepository.class);
    NamespaceRepository namespaces = mock(NamespaceRepository.class);
    NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    UserAccountRepository accounts = mock(UserAccountRepository.class);
    UserRoleBindingRepository roles = mock(UserRoleBindingRepository.class);
    SkillSubscriptionRepository subscriptions = mock(SkillSubscriptionRepository.class);
    // The actual recipient resolver and metadata policy are used; only authoritative storage facts are mocked.
    NotificationEmailEligibility eligibility = new NotificationEmailEligibility(skills, namespaces, accounts, members,
            subscriptions, new SubscriptionMetadataAccessPolicy(), new RecipientResolver(members, roles));
    NotificationEmailItem item(String type) {
        var now = Instant.parse("2026-10-22T00:00:00Z");
        return new NotificationEmailItem(
                1, 1, "admin", "event", NotificationCategory.PROMOTION, type, 55L, 8L, now, now, "ACTIVE");
    }
    @ParameterizedTest
    @ValueSource(strings = {"PROMOTION_SUBMITTED", "REPORT_SUBMITTED"})
    void administrativeRoleCannotAuthorizeAnAlreadyDeletedSkill(String eventType) {
        when(accounts.findById("admin")).thenReturn(Optional.of(new UserAccount("admin", "Admin", null, null)));
        when(skills.findById(55L)).thenReturn(Optional.empty());
        var role = new Role();
        ReflectionTestUtils.setField(role, "code", "SKILL_ADMIN");
        when(roles.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(new UserRoleBinding("admin", role)));
        assertThat(eligibility.eligible(item(eventType))).isFalse();
        verifyNoInteractions(roles);
    }
    @ParameterizedTest
    @ValueSource(strings = {"PROMOTION_SUBMITTED", "REPORT_SUBMITTED"})
    void existingSkillRequiresCurrentPlatformRoleAndStopsQualifyingAfterHardDeletion(String eventType) {
        when(accounts.findById("admin")).thenReturn(Optional.of(new UserAccount("admin", "Admin", null, null)));
        var skill = new Skill(5L, "private-skill", "author", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(skill, "id", 55L);
        when(skills.findById(55L)).thenReturn(Optional.of(skill));
        var role = new Role();
        ReflectionTestUtils.setField(role, "code", "SKILL_ADMIN");
        when(roles.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(new UserRoleBinding("admin", role)));
        assertThat(eligibility.eligible(item(eventType))).isTrue();
        when(roles.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN"))).thenReturn(List.of());
        assertThat(eligibility.eligible(item(eventType))).isFalse();
        when(roles.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(new UserRoleBinding("admin", role)));
        when(skills.findById(55L)).thenReturn(Optional.empty());
        assertThat(eligibility.eligible(item(eventType))).isFalse();
    }
}
