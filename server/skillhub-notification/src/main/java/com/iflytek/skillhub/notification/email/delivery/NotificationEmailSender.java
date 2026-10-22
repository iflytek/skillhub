package com.iflytek.skillhub.notification.email.delivery;
/** Transport must not join a database transaction. ACCEPTED does not mean delivered or read. */
public interface NotificationEmailSender {
    enum Result { ACCEPTED, TEMPORARY_FAILURE, PERMANENT_FAILURE, UNKNOWN }
    Result send(String recipient, String locale, long deliveryId, String subject, String text);
}
