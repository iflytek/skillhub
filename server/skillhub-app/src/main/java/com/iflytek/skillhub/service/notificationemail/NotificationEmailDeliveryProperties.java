package com.iflytek.skillhub.service.notificationemail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.notification.email.delivery")
public class NotificationEmailDeliveryProperties {
    private long pollDelayMs = 10000;
    private int leaseSeconds = 120;
    private int retryWindowHours = 24;
    private int batchSize = 20;
    private int sentRetentionDays = 30;
    private int failedRetentionDays = 90;

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long value) {
        if (value < 100) {
            throw new IllegalArgumentException("poll delay");
        }
        pollDelayMs = value;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int value) {
        if (value < 30) {
            throw new IllegalArgumentException("lease");
        }
        leaseSeconds = value;
    }

    public int getRetryWindowHours() {
        return retryWindowHours;
    }

    public void setRetryWindowHours(int value) {
        if (value < 1 || value > 168) {
            throw new IllegalArgumentException("retry window");
        }
        retryWindowHours = value;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int value) {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("batch size");
        }
        batchSize = value;
    }

    public int getSentRetentionDays() {
        return sentRetentionDays;
    }

    public void setSentRetentionDays(int value) {
        if (value < 8) {
            throw new IllegalArgumentException("retention must cover event acceptance window");
        }
        sentRetentionDays = value;
    }

    public int getFailedRetentionDays() {
        return failedRetentionDays;
    }

    public void setFailedRetentionDays(int value) {
        if (value < 8) {
            throw new IllegalArgumentException("retention must cover event acceptance window");
        }
        failedRetentionDays = value;
    }
}
