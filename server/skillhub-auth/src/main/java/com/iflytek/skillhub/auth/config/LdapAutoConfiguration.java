package com.iflytek.skillhub.auth.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.LdapContextSource;

/**
 * LDAP connection configuration, created only when {@code skillhub.ldap.enabled=true}.
 * <p>
 * The context source is the single place that maps {@link LdapProperties} onto JNDI
 * connection settings (URL, base, bind credentials, connect/read timeouts). A custom
 * trust store for LDAPS is installed JVM-wide before the context starts by
 * {@link LdapTrustStoreEnvironmentPostProcessor} (the JDK LDAP provider has no per-context
 * trust-store injection point).
 */
@Configuration
@ConditionalOnProperty(prefix = "skillhub.ldap", name = "enabled", havingValue = "true")
public class LdapAutoConfiguration {

    @Bean
    public LdapContextSource ldapContextSource(LdapProperties ldapProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrl());
        // The search base is applied explicitly by LdapAuthService (user-search-base + base), so
        // the context source must stay root-relative; otherwise the base would be applied twice
        // and every search would fail.
        if (ldapProperties.getUsername() != null && !ldapProperties.getUsername().isEmpty()) {
            contextSource.setUserDn(ldapProperties.getUsername());
            contextSource.setPassword(ldapProperties.getPassword());
        }
        contextSource.setPooled(false);

        Map<String, Object> baseEnvironment = new HashMap<>();
        baseEnvironment.put("com.sun.jndi.ldap.connect.timeout",
            String.valueOf(ldapProperties.getConnectTimeoutMillis()));
        baseEnvironment.put("com.sun.jndi.ldap.read.timeout",
            String.valueOf(ldapProperties.getReadTimeoutMillis()));
        contextSource.setBaseEnvironmentProperties(baseEnvironment);

        contextSource.afterPropertiesSet();
        return contextSource;
    }
}
