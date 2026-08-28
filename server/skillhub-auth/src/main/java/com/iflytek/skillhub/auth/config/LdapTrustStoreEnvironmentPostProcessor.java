package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.ldap.LdapTrustStoreInstaller;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Installs the custom LDAPS trust store before the Spring context (and therefore any TLS
 * connection) is created. Runs only when {@code skillhub.ldap.enabled=true} and
 * {@code skillhub.ldap.tls-trust-store} is set.
 */
public class LdapTrustStoreEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.parseBoolean(environment.getProperty("skillhub.ldap.enabled", "false"))) {
            return;
        }
        String path = environment.getProperty("skillhub.ldap.tls-trust-store", "");
        if (path == null || path.isBlank()) {
            return;
        }
        String password = environment.getProperty("skillhub.ldap.tls-trust-store-password", "");
        String type = environment.getProperty("skillhub.ldap.tls-trust-store-type", "JKS");
        LdapTrustStoreInstaller.install(path, password, type);
    }
}
