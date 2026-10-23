package com.iflytek.skillhub.service;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailAdmissionService implements NotificationEmailAdmissionProvider {
    private final NotificationEmailPolicyProvider policies;
    private final NotificationEmailRecipientProvider recipients;
    private final NotificationEmailPreferenceRepository preferences;

    public NotificationEmailAdmissionService(NotificationEmailPolicyProvider policies,
            NotificationEmailRecipientProvider recipients, NotificationEmailPreferenceRepository preferences) {
        this.policies = policies;
        this.recipients = recipients;
        this.preferences = preferences;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> currentUnderLocks(String userId, NotificationCategory category) {
        var policy = policies.current();
        var recipient = recipients.current(userId);
        var preference = preferences.findByUserIdOrderByCategoryAsc(userId).stream()
                .filter(row -> row.getCategory() == category).findFirst();
        if (!policy.enabled() || policy.enabledSince() == null || recipient.isEmpty()
                || recipient.get().eligibleSince() == null || preference.isEmpty()
                || preference.get().getMode() == NotificationEmailMode.OFF
                || preference.get().getEnabledSince() == null)
            return Optional.empty();
        return Optional.of(java.util.stream.Stream.of(policy.enabledSince(), recipient.get().eligibleSince(),
                        preference.get().getEnabledSince())
                .max(Instant::compareTo).orElseThrow());
    }
}
