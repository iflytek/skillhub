package com.iflytek.skillhub.auth.config;

import org.springframework.context.annotation.Configuration;

/**
 * LDAP configuration marker.
 * <p>
 * The actual LDAP connection handling is encapsulated inside {@code LdapAuthService}, which
 * uses JNDI {@code DirContext} directly. This avoids maintaining a parallel Spring LDAP
 * {@code LdapTemplate}/{@code LdapContextSource} bean graph whose configuration source
 * ({@code spring.ldap.*}) would diverge from the application-level {@code skillhub.ldap.*}
 * properties consumed by {@link LdapProperties}.
 * <p>
 * {@link LdapProperties} is a standalone {@code @Component} and is always available; the
 * {@code LdapAuthService} bean itself is conditionally created only when
 * {@code skillhub.ldap.enabled=true}.
 */
@Configuration
public class LdapAutoConfiguration {
}
