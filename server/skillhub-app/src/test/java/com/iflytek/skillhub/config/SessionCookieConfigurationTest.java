package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;

class SessionCookieConfigurationTest {

    @Test
    void cookieSerializer_supportsConfiguredSameSiteAndSecure() {
        ServerProperties properties = new ServerProperties();
        properties.getServlet().getSession().getCookie().setSameSite(SameSite.NONE);
        properties.getServlet().getSession().getCookie().setSecure(true);

        CookieSerializer serializer = new SessionCookieConfiguration().cookieSerializer(properties);

        assertThat(serializer).isNotNull();
        assertThat(serializer).isInstanceOf(DefaultCookieSerializer.class);
        assertThat(ReflectionTestUtils.getField(serializer, "sameSite")).isEqualTo("None");
        assertThat(ReflectionTestUtils.getField(serializer, "useSecureCookie")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void cookieSerializer_doesNotRequireSecureOverride() {
        ServerProperties properties = new ServerProperties();

        CookieSerializer serializer = new SessionCookieConfiguration().cookieSerializer(properties);

        assertThat(serializer).isNotNull();
        assertThat(ReflectionTestUtils.getField(serializer, "useSecureCookie")).isNull();
    }
}
