package com.iflytek.skillhub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Keeps Spring Session cookie attributes aligned with the standard servlet
 * session settings so browser SSO flows behave the same in Redis and local
 * session modes.
 */
@Configuration
@ConditionalOnClass(DefaultCookieSerializer.class)
public class SessionCookieConfiguration {

    @Bean
    public CookieSerializer cookieSerializer(ServerProperties serverProperties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        SameSite sameSite = serverProperties.getServlet().getSession().getCookie().getSameSite();
        if (sameSite != null) {
            serializer.setSameSite(sameSite.attributeValue());
        }
        Boolean secure = serverProperties.getServlet().getSession().getCookie().getSecure();
        if (secure != null) {
            serializer.setUseSecureCookie(secure);
        }
        return serializer;
    }
}
