package com.iflytek.skillhub.domain.security;

public interface SecurityScanner {
    SecurityScanResponse scan(SecurityScanRequest request);
    boolean isHealthy();
    String getScannerType();
}
