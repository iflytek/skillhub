package com.iflytek.skillhub.infra.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityScanExceptionTest {

    @Test
    void constructorWithMessage_shouldSetMessage() {
        SecurityScanException ex = new SecurityScanException("scan failed");
        assertEquals("scan failed", ex.getMessage());
    }

    @Test
    void constructorWithMessageAndCause_shouldSetBoth() {
        Throwable cause = new RuntimeException("root");
        SecurityScanException ex = new SecurityScanException("scan failed", cause);
        assertEquals("scan failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
