package com.iflytek.skillhub.notification.email;

public record NotificationEmailRecipient(String userId, String email, long emailRevision, String locale) {}
