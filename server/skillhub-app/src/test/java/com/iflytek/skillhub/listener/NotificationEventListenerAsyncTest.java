package com.iflytek.skillhub.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

class NotificationEventListenerAsyncTest {

    private static final Set<String> EVENT_HANDLER_METHODS = Set.of(
            "onSkillPublished",
            "onReviewSubmitted",
            "onReviewApproved",
            "onReviewRejected",
            "onPromotionSubmitted",
            "onPromotionApproved",
            "onPromotionRejected",
            "onReportSubmitted",
            "onReportResolved"
    );

    @Test
    void notificationHandlers_bindToSkillhubEventExecutor() {
        Set<String> testedMethods = Arrays.stream(NotificationEventListener.class.getDeclaredMethods())
                .filter(method -> EVENT_HANDLER_METHODS.contains(method.getName()))
                .peek(this::assertUsesNamedExecutor)
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(testedMethods).isEqualTo(EVENT_HANDLER_METHODS);
    }

    private void assertUsesNamedExecutor(Method method) {
        Async async = method.getAnnotation(Async.class);
        assertThat(async)
                .withFailMessage("Expected @Async on %s", method.getName())
                .isNotNull();
        assertThat(async.value()).isEqualTo("skillhubEventExecutor");
    }
}
