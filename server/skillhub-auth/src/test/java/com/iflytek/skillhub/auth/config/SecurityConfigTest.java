package com.iflytek.skillhub.auth.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {

    @Test
    void hasSessionCookieDetectsSpringSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("SESSION", "session-id"));

        assertTrue(SecurityConfig.hasSessionCookie(request));
    }

    @Test
    void hasSessionCookieDetectsRequestedSessionIdFromServletContainer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestedSessionId("custom-session-id");

        assertTrue(SecurityConfig.hasSessionCookie(request));
    }

    @Test
    void hasSessionCookieIgnoresServerSideSessionWithoutClientSessionId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        assertFalse(SecurityConfig.hasSessionCookie(request));
    }

    @Test
    void hasSessionCookieIgnoresNonSessionCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", "csrf-token"));

        assertFalse(SecurityConfig.hasSessionCookie(request));
    }
}
