package com.iflytek.skillhub.domain.security;

public interface ScanTaskProducer {
    void publishScanTask(ScanTask task);
}
