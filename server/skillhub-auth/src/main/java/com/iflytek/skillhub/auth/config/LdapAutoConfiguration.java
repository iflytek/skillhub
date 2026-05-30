package com.iflytek.skillhub.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

/**
 * LDAP auto-configuration that creates LdapTemplate only when LDAP is enabled.
 * This avoids the startup side effects of spring-boot-starter-data-ldap's
 * auto-configuration when LDAP is disabled.
 */
@Configuration
@ConditionalOnProperty(name = "skillhub.ldap.enabled", havingValue = "true")
public class LdapAutoConfiguration {

    /**
     * Creates an LdapContextSource configured from skillhub.ldap properties.
     */
    @Bean
    public LdapContextSource ldapContextSource(LdapProperties ldapProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrl());
        contextSource.setBase(ldapProperties.getBase());
        if (ldapProperties.getUsername() != null && !ldapProperties.getUsername().isEmpty()) {
            contextSource.setUserDn(ldapProperties.getUsername());
            contextSource.setPassword(ldapProperties.getPassword());
        }
        return contextSource;
    }

    /**
     * Creates an LdapTemplate for LDAP operations.
     */
    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource ldapContextSource) {
        return new LdapTemplate(ldapContextSource);
    }
}
