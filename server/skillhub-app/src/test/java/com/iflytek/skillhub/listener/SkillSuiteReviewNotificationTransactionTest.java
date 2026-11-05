package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.SkillSuiteReviewSubmittedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises Spring's real transactional event delivery; database lifecycle coverage is separate. */
@SpringBootTest
@ActiveProfiles("test")
class SkillSuiteReviewNotificationTransactionTest {

    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;
    @MockBean private SkillSuiteRepository suites;
    @MockBean private SkillSuiteVersionRepository versions;
    @MockBean private NamespaceRepository namespaces;
    @MockBean private RecipientResolver recipients;
    @MockBean private NotificationService notifications;

    private final SkillSuiteReviewSubmittedEvent event =
            new SkillSuiteReviewSubmittedEvent(912350L, 912351L, 912352L, "author", 912353L);

    @BeforeEach
    void setUp() {
        SkillSuite suite = new SkillSuite(912353L, "suite-event-test", "Suite", "author");
        ReflectionTestUtils.setField(suite, "id", 912351L);
        SkillSuiteVersion version = new SkillSuiteVersion(
                912351L, "1.0.0", "Suite snapshot", "Summary", SkillVisibility.PUBLIC, "author");
        Namespace namespace = new Namespace("suite-review-team", "Team", "owner");
        when(suites.findById(912351L)).thenReturn(Optional.of(suite));
        when(versions.findById(912352L)).thenReturn(Optional.of(version));
        when(namespaces.findById(912353L)).thenReturn(Optional.of(namespace));
        when(recipients.resolveNamespaceAdmins(912353L)).thenReturn(List.of("suite-review-admin"));
    }

    @Test
    void committedSubmissionNotifiesOnlyAfterTheTransactionCompletes() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event);
            verify(notifications, never()).create(any(), any(), any(), any(), any(), any(), any());
        });

        verify(notifications, timeout(5000)).create(eq("suite-review-admin"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(912350L));
    }

    @Test
    void rolledBackSubmissionDoesNotNotify() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(notifications, after(500).never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void eventOutsideATransactionDoesNotBypassTheCommitBoundary() {
        events.publishEvent(event);

        verify(notifications, after(500).never()).create(any(), any(), any(), any(), any(), any(), any());
    }
}
