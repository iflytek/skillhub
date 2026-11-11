package com.iflytek.skillhub.notification.email.delivery;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import java.util.Set;
/** Called inside the upstream settings/account/contact/preference transaction, after its locks. */
public interface NotificationEmailCancellation {
    void cancelAll();
    void cancelCategories(String userId, Set<NotificationCategory> categories);
}
