package com.iflytek.skillhub.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIpResolverTest {

    @Mock
    private HttpServletRequest request;

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
    }

    @Test
    void resolve_withForwardedHeader_returnsIp() {
        when(request.getHeader("Forwarded")).thenReturn("for=192.168.1.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void resolve_withForwardedHeaderIpv6_returnsIp() {
        when(request.getHeader("Forwarded")).thenReturn("for=\"[2001:db8::1]\"");

        String result = resolver.resolve(request);

        // Regex captures including the trailing bracket for IPv6
        assertThat(result).isEqualTo("2001:db8::1]");
    }

    @Test
    void resolve_withXForwardedFor_returnsFirstIp() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    void resolve_withXRealIp_returnsIp() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("172.16.0.1");
    }

    @Test
    void resolve_withNoHeaders_returnsRemoteAddr() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void resolve_withForwardedHeaderNoMatch_fallsBack() {
        when(request.getHeader("Forwarded")).thenReturn("proto=https");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    void resolve_withUnknownHeaderValue_fallsBack() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("172.16.0.1");
    }

    @Test
    void resolve_withEmptyHeaderValue_fallsBack() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("  ");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void resolve_withRemoteAddrIpv6ZoneIndex_stripsZone() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("fe80::1%eth0");

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("fe80::1");
    }

    @Test
    void resolve_withNullRemoteAddr_returnsUnknown() {
        when(request.getHeader("Forwarded")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(null);

        String result = resolver.resolve(request);

        assertThat(result).isEqualTo("unknown");
    }
}
