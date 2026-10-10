package com.iflytek.skillhub.infra.scanner;

public class SecurityScanException extends RuntimeException {

    public SecurityScanException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityScanException(String message) {
        super(message);
    }

    /**
     * Returns true when retrying later is safer than permanently failing the skill version.
     */
    public boolean isScannerUnavailable() {
        if (!(getCause() instanceof com.iflytek.skillhub.infra.http.HttpClientException error)) {
            return false;
        }
        int status = error.getStatusCode();
        return status == 0 || status == 429 || status >= 500;
    }
}
