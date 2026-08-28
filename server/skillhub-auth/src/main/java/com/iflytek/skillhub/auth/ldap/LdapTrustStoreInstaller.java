package com.iflytek.skillhub.auth.ldap;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Installs a custom trust store for LDAPS by merging it with the JVM's default trust store and
 * pointing the {@code javax.net.ssl.trustStore*} system properties at the merged store.
 * <p>
 * This is the only reliable injection point for LDAPS trust in a JNDI-based client: the JDK
 * LDAP provider builds its sockets from the JVM-wide SSL configuration and ignores both
 * {@code javax.net.ssl.trustStore*} context-environment entries and the
 * {@code java.naming.ldap.factory.socket} property (verified against the JDK 21 implementation),
 * and Spring LDAP 3.x no longer offers a per-context socket factory. Because the JSSE default
 * SSLContext is cached on first use, the installer must run before any TLS connection is made;
 * it is invoked from an {@code EnvironmentPostProcessor} during application startup.
 * <p>
 * Merging (rather than replacing) keeps public-CA connectivity intact: the resulting store
 * contains the JVM defaults plus the configured internal CA.
 */
public final class LdapTrustStoreInstaller {

    private static final String DEFAULT_TRUSTSTORE_PASSWORD = "changeit";

    private LdapTrustStoreInstaller() {
    }

    /**
     * Merges the configured custom trust store into the JVM default trust store and installs the
     * result through the {@code javax.net.ssl.trustStore*} system properties.
     *
     * @param customPath the custom trust store path
     * @param customPassword the custom trust store password (may be empty)
     * @param customType the custom trust store type (JKS, PKCS12, ...)
     */
    public static void install(String customPath, String customPassword, String customType) {
        try {
            KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
            merged.load(null, null);
            copyCertificateEntries(loadDefaultTrustStore(), merged);
            copyCertificateEntries(loadCustomTrustStore(customPath, customPassword, customType), merged);

            String password = "skillhub-" + UUID.randomUUID();
            Path file = Files.createTempFile("skillhub-truststore", ".p12");
            try (OutputStream out = Files.newOutputStream(file)) {
                merged.store(out, password.toCharArray());
            }
            System.setProperty("javax.net.ssl.trustStore", file.toString());
            System.setProperty("javax.net.ssl.trustStorePassword", password);
            System.setProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to install LDAPS trust store from " + customPath, e);
        }
    }

    private static KeyStore loadDefaultTrustStore() throws Exception {
        String systemPath = System.getProperty("javax.net.ssl.trustStore");
        String systemPassword = System.getProperty("javax.net.ssl.trustStorePassword",
            DEFAULT_TRUSTSTORE_PASSWORD);
        String systemType = System.getProperty("javax.net.ssl.trustStoreType",
            KeyStore.getDefaultType());
        Path path = systemPath != null && !systemPath.isEmpty()
            ? Path.of(systemPath)
            : Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        KeyStore keyStore = KeyStore.getInstance(systemType);
        try (InputStream in = Files.newInputStream(path)) {
            keyStore.load(in, systemPassword.toCharArray());
        }
        return keyStore;
    }

    private static KeyStore loadCustomTrustStore(String path, String password, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            keyStore.load(in, password.toCharArray());
        }
        return keyStore;
    }

    private static void copyCertificateEntries(KeyStore source, KeyStore target) throws Exception {
        Enumeration<String> aliases = source.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (source.isCertificateEntry(alias)) {
                target.setCertificateEntry(alias, source.getCertificate(alias));
            }
        }
    }
}
