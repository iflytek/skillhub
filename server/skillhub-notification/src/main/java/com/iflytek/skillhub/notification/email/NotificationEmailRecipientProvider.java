package com.iflytek.skillhub.notification.email;

public interface NotificationEmailRecipientProvider {
    java.util.Optional<NotificationEmailRecipient> current(String userId);
    java.util.Optional<NotificationEmailRecipient> lockCurrent(String userId);
}
