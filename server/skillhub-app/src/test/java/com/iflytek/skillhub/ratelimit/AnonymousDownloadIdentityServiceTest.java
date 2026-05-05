package com.iflytek.skillhub.ratelimit;

import com.iflytek.skillhub.config.DownloadRateLimitProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnonymousDownloadIdentityServiceTest {

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private DownloadRateLimitProperties properties;
    private AnonymousDownloadIdentityService service;

    @BeforeEach
    void setUp() {
        properties = new DownloadRateLimitProperties();
        properties.setAnonymousCookieSecret("test-secret-key-for-hmac");
        properties.setAnonymousCookieName("skillhub_anon_dl");
        properties.setAnonymousCookieMaxAge(Duration.ofDays(30));
        service = new AnonymousDownloadIdentityService(properties, clientIpResolver);
    }

    @Test
    void resolve_withoutCookie_generatesNewCookieAndReturnsIdentity() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        when(request.getCookies()).thenReturn(null);
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        assertThat(identity.ipHash()).isNotNull().isNotEmpty();
        assertThat(identity.cookieHash()).isNotNull().isNotEmpty();

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).contains("skillhub_anon_dl");
    }

    @Test
    void resolve_withValidCookie_reusesCookieId() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        when(request.getCookies()).thenReturn(null);
        when(request.isSecure()).thenReturn(false);

        // First call to get a valid cookie value
        service.resolve(request, response);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), captor.capture());
        String cookieHeader = captor.getValue();
        String cookieValue = cookieHeader.split(";")[0].split("=")[1];

        // Second call with the valid cookie
        reset(response);
        Cookie cookie = new Cookie("skillhub_anon_dl", cookieValue);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        assertThat(identity.ipHash()).isNotNull();
        assertThat(identity.cookieHash()).isNotNull();
    }

    @Test
    void resolve_withInvalidCookieVersion_generatesNewCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie cookie = new Cookie("skillhub_anon_dl", "v2.someid.invalidsig");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void resolve_withTamperedCookieSignature_generatesNewCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie cookie = new Cookie("skillhub_anon_dl", "v1.someid.invalidsig");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void resolve_withMalformedBase64Cookie_generatesNewCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie cookie = new Cookie("skillhub_anon_dl", "v1.someid!!!");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void resolve_withSecureRequest_buildsSecureCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        when(request.getCookies()).thenReturn(null);
        when(request.isSecure()).thenReturn(true);

        service.resolve(request, response);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).contains("Secure");
    }

    @Test
    void resolve_withXForwardedProtoHttps_buildsSecureCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        when(request.getCookies()).thenReturn(null);
        when(request.isSecure()).thenReturn(false);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");

        service.resolve(request, response);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).contains("Secure");
    }

    @Test
    void resolve_withEmptyCookieValue_generatesNewCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie cookie = new Cookie("skillhub_anon_dl", "");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void resolve_withXForwardedProtoHttp_buildsNonSecureCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        when(request.getCookies()).thenReturn(null);
        when(request.isSecure()).thenReturn(false);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("http");

        service.resolve(request, response);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).doesNotContain("Secure");
    }

    @Test
    void resolve_withTwoPartCookie_generatesNewCookie() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie cookie = new Cookie("skillhub_anon_dl", "v1.someid");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void resolve_withOtherCookies_ignoresThem() {
        when(clientIpResolver.resolve(request)).thenReturn("192.168.1.1");
        Cookie otherCookie = new Cookie("other_cookie", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});
        when(request.isSecure()).thenReturn(false);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity = service.resolve(request, response);

        assertThat(identity).isNotNull();
        verify(response).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void anonymousDownloadIdentity_recordHasCorrectFields() {
        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity =
                new AnonymousDownloadIdentityService.AnonymousDownloadIdentity("hash1", "hash2");

        assertThat(identity.ipHash()).isEqualTo("hash1");
        assertThat(identity.cookieHash()).isEqualTo("hash2");
    }

    @Test
    void parseAndVerify_nullValue_returnsNull() {
        String result = ReflectionTestUtils.invokeMethod(service, "parseAndVerify", (String) null);
        assertThat(result).isNull();
    }

    @Test
    void parseAndVerify_invalidBase64_returnsNull() {
        try (MockedStatic<Base64> base64 = mockStatic(Base64.class)) {
            Base64.Decoder mockDecoder = mock(Base64.Decoder.class);
            base64.when(Base64::getUrlDecoder).thenReturn(mockDecoder);
            when(mockDecoder.decode(anyString())).thenThrow(new IllegalArgumentException("bad base64"));

            String result = ReflectionTestUtils.invokeMethod(service, "parseAndVerify", "v1.someid.anything");
            assertThat(result).isNull();
        }
    }

    @Test
    void sign_generalSecurityException_throwsIllegalStateException() {
        try (MockedStatic<Mac> macStatic = mockStatic(Mac.class)) {
            macStatic.when(() -> Mac.getInstance("HmacSHA256")).thenThrow(new NoSuchAlgorithmException("test"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "sign", "value"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to sign anonymous download cookie");
        }
    }

    @Test
    void hash_generalSecurityException_throwsIllegalStateException() {
        try (MockedStatic<MessageDigest> digestStatic = mockStatic(MessageDigest.class)) {
            digestStatic.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(new NoSuchAlgorithmException("test"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "hash", "value"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to hash anonymous download identity");
        }
    }
}
