package com.iflytek.skillhub.auth.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.data.redis.serializer.SerializationException;

class ExpiredPublicSessionFilterTest {

    private final CorruptSessionRemover corruptSessionRemover = mock(CorruptSessionRemover.class);
    private final ExpiredPublicSessionFilter filter = new ExpiredPublicSessionFilter(
            new RouteSecurityPolicyRegistry(), corruptSessionRemover, "SESSION");

    @Test
    void expiredSessionOnPublicRoute_shouldBeHiddenFromDownstreamSecurityFilters() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/api/v1/skills");
        request.setCookies(
                new Cookie("SESSION", "expired"),
                new Cookie("JSESSIONID", "expired-servlet"),
                new Cookie("locale", "zh"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest downstream = capturedRequest(chain);
        assertNull(downstream.getRequestedSessionId());
        assertArrayEquals(new String[]{"locale"},
                java.util.Arrays.stream(downstream.getCookies()).map(Cookie::getName).toArray(String[]::new));
    }

    @Test
    void expiredSessionOnProtectedMethod_shouldRemainVisibleForUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("POST", "/api/v1/skills");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, capturedRequest(chain));
        assertEquals("expired", request.getRequestedSessionId());
    }

    @Test
    void validSessionOnPublicRoute_shouldRemainUnchanged() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/api/v1/search");
        request.setRequestedSessionIdValid(true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, capturedRequest(chain));
    }

    @Test
    void forwardedPrefix_shouldUseServletPathForPublicRouteDecision() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/skillhub/api/v1/search");
        request.setContextPath("/skillhub");
        request.setServletPath("/api/v1/search");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(capturedRequest(chain).getRequestedSessionId());
    }

    @Test
    void unreadableSession_shouldBeDeletedAndTreatedAsLoggedOut() throws Exception {
        MockHttpServletRequest delegate = new MockHttpServletRequest();
        delegate.setMethod("GET");
        delegate.setRequestURI("/api/v1/auth/methods");
        delegate.setCookies(new Cookie("SESSION", "corrupt-session"));
        AtomicInteger calls = new AtomicInteger();
        HttpServletRequest request = new HttpServletRequestWrapper(delegate) {
            @Override
            public String getRequestedSessionId() {
                if (calls.getAndIncrement() == 0) {
                    throw new SerializationException("incompatible session data");
                }
                return "resolved-session-id";
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(corruptSessionRemover).remove("resolved-session-id");
        assertNull(capturedRequest(chain).getRequestedSessionId());
        assertNotNull(response.getCookie("SESSION"));
        assertEquals(0, response.getCookie("SESSION").getMaxAge());
    }

    @Test
    void unreadableSession_shouldNotHideRedisDeleteFailure() {
        MockHttpServletRequest delegate = new MockHttpServletRequest();
        delegate.setMethod("GET");
        delegate.setRequestURI("/api/v1/auth/methods");
        delegate.setCookies(new Cookie("SESSION", "encoded-cookie"));
        AtomicInteger calls = new AtomicInteger();
        HttpServletRequest request = new HttpServletRequestWrapper(delegate) {
            @Override
            public String getRequestedSessionId() {
                if (calls.getAndIncrement() == 0) {
                    throw new SerializationException("incompatible session data");
                }
                return "resolved-session-id";
            }
        };
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        org.mockito.Mockito.doThrow(redisFailure)
                .when(corruptSessionRemover).remove("resolved-session-id");
        FilterChain chain = mock(FilterChain.class);

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), chain));

        assertSame(redisFailure, actual);
        verifyNoInteractions(chain);
    }

    private static MockHttpServletRequest expiredSessionRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setRequestedSessionId("expired");
        request.setRequestedSessionIdValid(false);
        return request;
    }

    private static HttpServletRequest capturedRequest(FilterChain chain) throws Exception {
        ArgumentCaptor<ServletRequest> requestCaptor = ArgumentCaptor.forClass(ServletRequest.class);
        ArgumentCaptor<ServletResponse> responseCaptor = ArgumentCaptor.forClass(ServletResponse.class);
        verify(chain).doFilter(requestCaptor.capture(), responseCaptor.capture());
        return (HttpServletRequest) requestCaptor.getValue();
    }
}
