package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Enumeration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LdapTrustStoreInstallerTest {

    @TempDir
    Path tempDir;

    private String previousTrustStore;
    private String previousPassword;
    private String previousType;

    @AfterEach
    void restoreSystemProperties() {
        restore("javax.net.ssl.trustStore", previousTrustStore);
        restore("javax.net.ssl.trustStorePassword", previousPassword);
        restore("javax.net.ssl.trustStoreType", previousType);
    }

    @Test
    void install_mergesCustomTrustStoreIntoDefaults() throws Exception {
        previousTrustStore = System.getProperty("javax.net.ssl.trustStore");
        previousPassword = System.getProperty("javax.net.ssl.trustStorePassword");
        previousType = System.getProperty("javax.net.ssl.trustStoreType");

        // Build a custom trust store containing one certificate copied from the JVM defaults.
        KeyStore defaults = defaultTrustStore();
        String sourceAlias = firstCertificateAlias(defaults);
        Path custom = tempDir.resolve("custom.p12");
        KeyStore customStore = KeyStore.getInstance("PKCS12");
        customStore.load(null, null);
        customStore.setCertificateEntry("custom-ca", defaults.getCertificate(sourceAlias));
        try (OutputStream out = Files.newOutputStream(custom)) {
            customStore.store(out, "changeit".toCharArray());
        }

        LdapTrustStoreInstaller.install(custom.toString(), "changeit", "PKCS12");

        String installedPath = System.getProperty("javax.net.ssl.trustStore");
        assertThat(installedPath).isNotBlank();
        KeyStore installed = KeyStore.getInstance(System.getProperty("javax.net.ssl.trustStoreType"));
        try (InputStream in = Files.newInputStream(Path.of(installedPath))) {
            installed.load(in, System.getProperty("javax.net.ssl.trustStorePassword").toCharArray());
        }
        assertThat(installed.containsAlias("custom-ca")).as("custom CA is merged").isTrue();
        assertThat(installed.containsAlias(sourceAlias)).as("default certificates are preserved").isTrue();
    }

    @Test
    void install_withMissingFile_failsFast() {
        assertThatThrownBy(() -> LdapTrustStoreInstaller.install(
            tempDir.resolve("missing.p12").toString(), "changeit", "PKCS12"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to install LDAPS trust store");
    }

    private static KeyStore defaultTrustStore() throws Exception {
        String systemPath = System.getProperty("javax.net.ssl.trustStore");
        Path path = systemPath != null && !systemPath.isEmpty()
            ? Path.of(systemPath)
            : Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(path)) {
            keyStore.load(in, "changeit".toCharArray());
        }
        return keyStore;
    }

    private static String firstCertificateAlias(KeyStore keyStore) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isCertificateEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("default trust store has no certificate entries");
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
