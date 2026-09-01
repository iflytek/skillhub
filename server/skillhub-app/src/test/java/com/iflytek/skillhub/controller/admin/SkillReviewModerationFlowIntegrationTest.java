package com.iflytek.skillhub.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillReviewStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.AuditLogJpaRepository;
import com.iflytek.skillhub.infra.jpa.JpaSkillRatingRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SkillhubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillReviewModerationFlowIntegrationTest {

    private static final String ADMIN_ID = "review-admin";
    private static final String AUTHOR_ID = "review-author";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountJpaRepository userAccountRepository;
    @Autowired private NamespaceJpaRepository namespaceRepository;
    @Autowired private SkillJpaRepository skillRepository;
    @Autowired private SkillRatingRepository ratingRepository;
    @Autowired private JpaSkillRatingRepository ratingJpaRepository;

    @Autowired private AuditLogRepository auditLogRepository;
    @SpyBean private AuditLogJpaRepository auditLogJpaRepository;
    @MockBean private DeviceAuthService deviceAuthService;
    @MockBean private RbacService rbacService;
    @MockBean private GovernanceNotificationService governanceNotificationService;
    @MockBean private NotificationDispatcher notificationDispatcher;

    @Test
    void hideAndRestorePersistModerationStateAndAuditRows() throws Exception {
        SkillRating review = createReview();

        mockMvc.perform(post("/api/v1/admin/skill-reviews/" + review.getId() + "/hide")
                        .contentType("application/json")
                        .content("{\"reason\":\"off topic\"}")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        SkillRating hidden = ratingRepository.findById(review.getId()).orElseThrow();
        assertThat(hidden.getReviewStatus()).isEqualTo(SkillReviewStatus.HIDDEN);
        assertThat(hidden.getModeratedBy()).isEqualTo(ADMIN_ID);
        assertThat(hidden.getModerationReason()).isEqualTo("off topic");

        mockMvc.perform(post("/api/v1/admin/skill-reviews/" + review.getId() + "/restore")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));

        SkillRating saved = ratingRepository.findById(review.getId()).orElseThrow();
        assertThat(saved.getReviewStatus()).isEqualTo(SkillReviewStatus.VISIBLE);
        assertThat(saved.getModeratedBy()).isEqualTo(ADMIN_ID);

        List<String> actions = auditLogJpaRepository.findAll().stream()
                .filter(log -> ADMIN_ID.equals(log.getActorUserId()))
                .filter(log -> review.getId().equals(log.getTargetId()))
                .filter(log -> "SKILL_REVIEW".equals(log.getTargetType()))
                .map(AuditLog::getAction)
                .toList();
        assertThat(actions).contains("SKILL_REVIEW_HIDE", "SKILL_REVIEW_RESTORE");
    }

    @Test
    void hideRollsBackModerationWhenAuditPersistenceFails() throws Exception {
        SkillRating review = createReview();
        doThrow(new DataIntegrityViolationException("forced audit failure"))
                .when(auditLogRepository).save(any(AuditLog.class));

        mockMvc.perform(post("/api/v1/admin/skill-reviews/" + review.getId() + "/hide")
                        .contentType("application/json")
                        .content("{\"reason\":\"off topic\"}")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());

        SkillRating saved = ratingRepository.findById(review.getId()).orElseThrow();
        assertThat(saved.getReviewStatus()).isEqualTo(SkillReviewStatus.VISIBLE);
        assertThat(saved.getModeratedBy()).isNull();
        assertThat(saved.getModerationReason()).isNull();
    }

    private SkillRating createReview() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        saveUserIfAbsent(ADMIN_ID, "Review Admin", "review-admin@example.test");
        saveUserIfAbsent(AUTHOR_ID, "Review Author", "review-author@example.test");

        Namespace namespace = namespaceRepository.saveAndFlush(
                new Namespace("review-team-" + suffix, "Review Team " + suffix, AUTHOR_ID));
        Skill skill = new Skill(namespace.getId(), "review-skill-" + suffix, AUTHOR_ID, SkillVisibility.PUBLIC);
        skill.setCreatedBy(AUTHOR_ID);
        skill.setUpdatedBy(AUTHOR_ID);
        skill = skillRepository.saveAndFlush(skill);

        SkillRating review = new SkillRating(skill.getId(), AUTHOR_ID, (short) 4);
        review.updateReview((short) 4, "Useful review");
        return ratingJpaRepository.saveAndFlush(review);
    }

    private void saveUserIfAbsent(String userId, String displayName, String email) {
        if (!userAccountRepository.existsById(userId)) {
            userAccountRepository.saveAndFlush(new UserAccount(userId, displayName, email, null));
        }
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                ADMIN_ID,
                "Review Admin",
                "review-admin@example.test",
                "",
                "session",
                Set.of("SKILL_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );
    }
}
