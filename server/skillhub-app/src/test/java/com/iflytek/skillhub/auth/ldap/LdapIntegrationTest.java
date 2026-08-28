package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.service.LdapBindingAppService;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end LDAP coverage against a real OpenLDAP directory (Testcontainers) for the behavior
 * required by PR #437 review: first-login provisioning, repeat-login identity stability,
 * no-email placeholder handling, email-collision refusal, invalid credentials, attribute
 * synchronization, and LDAPS TLS-failure classification.
 *
 * <p>The default {@code subject-attribute=entryUUID} is intentionally left untouched: OpenLDAP
 * exposes entryUUID only as an operational attribute, which previously made every login fail
 * with a 503 unless the operator changed the subject attribute.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LdapIntegrationTest {

    private static final String BASE_DN = "dc=example,dc=org";
    private static final String BIND_DN = "cn=admin," + BASE_DN;
    private static final String BIND_PASSWORD = "admin";

    /**
     * The image's baked-in TLS certificates expired in 2026, which makes any LDAPS success path
     * impossible. Generate a fresh CA and server certificate with the JDK's keytool before the
     * container starts, and let {@code withCopyFileToContainer} install them over the baked-in
     * files. The container's entrypoint only generates certificates when the files are absent.
     */
    private static final Path TLS_CERTS_DIR = prepareTlsCertificates();

    /**
     * The JDK LDAP provider resolves LDAPS trust from the JVM-wide SSL configuration and offers
     * no per-connection trust-store injection point, so the test CA must be installed through the
     * {@code javax.net.ssl.trustStore*} system properties before any JNDI connection (and thus
     * before the JSSE default SSLContext is cached). The static initializer runs at class load,
     * before the Spring context and the LDAP container are created.
     */
    static {
        try {
            Path truststore = Files.createTempFile("ldap-truststore", ".p12");
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream in = Files.newInputStream(TLS_CERTS_DIR.resolve("ca.crt"))) {
                ks.setCertificateEntry("ldap-ca", cf.generateCertificate(in));
            }
            try (OutputStream out = Files.newOutputStream(truststore)) {
                ks.store(out, "changeit".toCharArray());
            }
            System.setProperty("javax.net.ssl.trustStore", truststore.toString());
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Container
    static final GenericContainer<?> LDAP = new GenericContainer<>("osixia/openldap:1.5.0")
        .withEnv("LDAP_ORGANISATION", "Example Inc")
        .withEnv("LDAP_DOMAIN", "example.org")
        .withEnv("LDAP_ADMIN_PASSWORD", BIND_PASSWORD)
        // The image defaults to olcTLSVerifyClient=demand, which requires client certificates
        // during the TLS handshake. The tests exercise server-certificate validation only.
        .withEnv("LDAP_TLS_VERIFY_CLIENT", "never")
        .withExposedPorts(389, 636)
        .withCopyFileToContainer(MountableFile.forHostPath(TLS_CERTS_DIR.resolve("ca.crt")),
            "/container/service/slapd/assets/certs/ca.crt")
        .withCopyFileToContainer(MountableFile.forHostPath(TLS_CERTS_DIR.resolve("ldap.crt")),
            "/container/service/slapd/assets/certs/ldap.crt")
        .withCopyFileToContainer(MountableFile.forHostPath(TLS_CERTS_DIR.resolve("ldap.key")),
            "/container/service/slapd/assets/certs/ldap.key");

    private static Path prepareTlsCertificates() {
        try {
            Path dir = Files.createTempDirectory("ldap-tls-certs");
            runKeytool(dir, List.of("keytool", "-genkeypair", "-alias", "ca",
                "-dname", "CN=SkillHub Test CA", "-validity", "3650", "-keyalg", "RSA",
                "-sigalg", "SHA256withRSA", "-storetype", "PKCS12", "-keystore", "ca.p12",
                "-storepass", "changeit", "-keypass", "changeit",
                "-ext", "BasicConstraints=ca:true"));
            runKeytool(dir, List.of("keytool", "-genkeypair", "-alias", "server",
                "-dname", "CN=ldap.example.org", "-validity", "3650", "-keyalg", "RSA",
                "-sigalg", "SHA256withRSA", "-storetype", "PKCS12", "-keystore", "server.p12",
                "-storepass", "changeit", "-keypass", "changeit"));
            runKeytool(dir, List.of("keytool", "-certreq", "-alias", "server",
                "-keystore", "server.p12", "-storepass", "changeit", "-file", "server.csr"));
            runKeytool(dir, List.of("keytool", "-gencert", "-alias", "ca",
                "-keystore", "ca.p12", "-storepass", "changeit", "-infile", "server.csr",
                "-rfc", "-validity", "3650",
                "-ext", "BasicConstraints=ca:false",
                "-ext", "KeyUsage=digitalSignature,keyEncipherment",
                "-ext", "ExtendedKeyUsage=serverAuth",
                "-outfile", "server.crt"));
            runKeytool(dir, List.of("keytool", "-exportcert", "-alias", "ca",
                "-keystore", "ca.p12", "-storepass", "changeit", "-rfc", "-file", "ca.crt"));

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(dir.resolve("server.p12"))) {
                ks.load(in, "changeit".toCharArray());
            }
            PrivateKey key = (PrivateKey) ks.getKey("server", "changeit".toCharArray());
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
            Files.writeString(dir.resolve("ldap.key"), pem);
            // slapd runs as the openldap user; the key must be world-readable inside the container.
            Files.setPosixFilePermissions(dir.resolve("ldap.key"),
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            Files.copy(dir.resolve("server.crt"), dir.resolve("ldap.crt"));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare LDAPS test certificates", e);
        }
    }

    private static void runKeytool(Path dir, List<String> command) throws Exception {
        Process process = new ProcessBuilder(command)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(command.get(0) + " failed (" + exit + "): " + output);
        }
    }

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("skillhub.ldap.enabled", () -> "true");
        registry.add("skillhub.ldap.url", () -> "ldap://" + LDAP.getHost() + ":" + LDAP.getMappedPort(389));
        registry.add("skillhub.ldap.base", () -> BASE_DN);
        registry.add("skillhub.ldap.username", () -> BIND_DN);
        registry.add("skillhub.ldap.password", () -> BIND_PASSWORD);
        registry.add("skillhub.ldap.user-search-attribute", () -> "uid");
        // subject-attribute intentionally stays at its default (entryUUID).
    }

    @Autowired
    private LocalAuthService localAuthService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private IdentityBindingRepository identityBindingRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private LdapBindingAppService ldapBindingAppService;

    @BeforeEach
    void ensureGlobalNamespace() {
        namespaceRepository.findBySlug("global")
            .orElseGet(() -> namespaceRepository.save(new Namespace("global", "Global", "bootstrap")));
    }

    @BeforeAll
    static void seedDirectory() throws Exception {
        // The OpenLDAP container's certificate is issued for cn=ldap.example.org, while the test
        // connects through the container's mapped address. The JNDI LDAP provider performs
        // endpoint identification (hostname verification) by default, which would reject the
        // address even with a trusted CA. Disable endpoint identification for this test JVM so
        // the custom-truststore success path exercises certificate-chain validation only.
        System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", "true");
        LDAP.copyFileToContainer(MountableFile.forClasspathResource("ldap/seed-users.ldif"), "/tmp/seed-users.ldif");
        LDAP.copyFileToContainer(MountableFile.forClasspathResource("ldap/modify-dave.ldif"), "/tmp/modify-dave.ldif");
        awaitLdapReady();
        ExecResult add = LDAP.execInContainer("ldapadd", "-x", "-H", "ldap://localhost",
            "-D", BIND_DN, "-w", BIND_PASSWORD, "-f", "/tmp/seed-users.ldif");
        assertThat(add.getExitCode())
            .as("ldapadd failed: %s", add.getStdout() + add.getStderr())
            .isZero();
    }

    @AfterAll
    static void restoreEndpointIdentification() {
        System.clearProperty("com.sun.jndi.ldap.object.disableEndpointIdentification");
    }

    private static void awaitLdapReady() throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                ExecResult r = LDAP.execInContainer("ldapsearch", "-x", "-H", "ldap://localhost",
                    "-b", BASE_DN, "-D", BIND_DN, "-w", BIND_PASSWORD, "(objectClass=*)", "dn");
                if (r.getExitCode() == 0) {
                    return;
                }
                last = new IllegalStateException("ldapsearch exit " + r.getExitCode()
                    + ": " + r.getStdout() + r.getStderr());
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("OpenLDAP did not become ready", last);
    }

    @Test
    void firstLogin_withDefaultEntryUuidSubject_provisionsAccountAndBinding() throws Exception {
        PlatformPrincipal principal = localAuthService.login("alice", "alice123");

        assertThat(principal.userId()).isNotBlank();
        Optional<UserAccount> account = userAccountRepository.findById(principal.userId());
        assertThat(account).isPresent();
        assertThat(account.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(account.get().getDisplayName()).isEqualTo("Alice Smith");

        String entryUuid = directoryEntryUuid("alice");
        Optional<IdentityBinding> binding =
            identityBindingRepository.findByProviderCodeAndSubject("ldap", entryUuid);
        assertThat(binding).as("identity binding keyed by OpenLDAP entryUUID").isPresent();
    }

    @Test
    void repeatLogin_returnsSameAccount_andDoesNotDuplicate() throws Exception {
        PlatformPrincipal first = localAuthService.login("alice", "alice123");
        PlatformPrincipal second = localAuthService.login("alice", "alice123");

        assertThat(second.userId()).isEqualTo(first.userId());
        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase("alice@example.com");
        assertThat(account).isPresent();
        assertThat(account.get().getId()).isEqualTo(first.userId());
        // Exactly one binding exists for this subject; no duplicate provisioning happened.
        Optional<IdentityBinding> binding =
            identityBindingRepository.findByProviderCodeAndSubject("ldap", directoryEntryUuid("alice"));
        assertThat(binding).isPresent();
        assertThat(binding.get().getUserId()).isEqualTo(first.userId());
    }

    @Test
    void noEmailUser_usesPlaceholderEmail_andCnFallback() {
        PlatformPrincipal principal = localAuthService.login("bob", "bob123");

        assertThat(principal.email()).isEqualTo("ldap:bob@internal");
        // bob has no displayName in the directory; the configured cn fallback must apply.
        assertThat(principal.displayName()).isEqualTo("Bob Jones");
    }

    @Test
    void emailCollision_returnsConflict_andDoesNotTakeOverExistingAccount() throws Exception {
        UserAccount local = new UserAccount("usr_local_carol", "Carol Local", "carol@example.com", null);
        userAccountRepository.save(local);

        assertThatThrownBy(() -> localAuthService.login("carol", "carol123"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.emailConflict");
            });

        // The existing account is untouched and no LDAP binding was written.
        Optional<UserAccount> untouched = userAccountRepository.findById(local.getId());
        assertThat(untouched).isPresent();
        assertThat(untouched.get().getDisplayName()).isEqualTo("Carol Local");
        assertThat(identityBindingRepository.findByProviderCodeAndSubject("ldap", directoryEntryUuid("carol")))
            .isEmpty();
    }

    @Test
    void wrongPassword_returnsUnauthorized() {
        assertThatThrownBy(() -> localAuthService.login("alice", "wrong-password"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> assertThat(((AuthFlowException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void emptyPassword_returnsUnauthorized_notServerError() {
        // Direct service-level coverage: a null password must be classified as a credential
        // failure (401), never a NullPointerException/500. (The HTTP layer already rejects
        // blank passwords via @NotBlank; this guards service-level callers.)
        assertThatThrownBy(() -> localAuthService.login("alice", null))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> assertThat(((AuthFlowException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void attributeChanges_areRefreshedOnNextLogin() throws Exception {
        PlatformPrincipal before = localAuthService.login("dave", "dave123");
        assertThat(before.displayName()).isEqualTo("Dave Miller");

        ExecResult mod = LDAP.execInContainer("ldapmodify", "-x", "-H", "ldap://localhost",
            "-D", BIND_DN, "-w", BIND_PASSWORD, "-f", "/tmp/modify-dave.ldif");
        assertThat(mod.getExitCode())
            .as("ldapmodify failed: %s", mod.getStdout() + mod.getStderr())
            .isZero();

        PlatformPrincipal after = localAuthService.login("dave", "dave123");
        assertThat(after.userId()).isEqualTo(before.userId());
        assertThat(after.displayName()).isEqualTo("Dave D. Miller");
    }

    @Test
    void ldaps_withCustomTrustStore_authenticatesSuccessfully() throws Exception {
        // The test CA is installed JVM-wide by the static initializer (the JDK LDAP provider has
        // no per-connection trust-store injection point). This test verifies the full LDAPS chain
        // (search, bind, attribute read) succeeds with that trust store in place.
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setUrl("ldaps://" + LDAP.getHost() + ":" + LDAP.getMappedPort(636));
        props.setBase(BASE_DN);
        props.setUsername(BIND_DN);
        props.setPassword(BIND_PASSWORD);

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IdentityBindingRepository bindingRepo = mock(IdentityBindingRepository.class);
        when(bindingRepo.findByProviderCodeAndSubject(any(), any())).thenReturn(Optional.empty());

        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(props.getUrl());
        contextSource.setUserDn(BIND_DN);
        contextSource.setPassword(BIND_PASSWORD);
        contextSource.setPooled(false);
        contextSource.afterPropertiesSet();

        LdapAuthService svc = new LdapAuthService(props,
            contextSource,
            userRepo,
            mock(UserRoleBindingRepository.class),
            mock(GlobalNamespaceMembershipService.class),
            bindingRepo,
            mock(EntityManager.class),
            mock(PlatformTransactionManager.class));

        PlatformPrincipal principal = svc.login("alice", "alice123");
        assertThat(principal.userId()).isNotBlank();
        assertThat(principal.email()).isEqualTo("alice@example.com");
    }

    @Test
    void explicitBind_attachesLdapIdentity_thenLdapLoginResolvesToBoundAccount() throws Exception {
        // Self-service binding: a local account proves ownership of the LDAP identity with the
        // directory password, and subsequent LDAP logins resolve to that account.
        UserAccount local = new UserAccount("usr_grace_bind", "Grace Local", "grace@example.com", null);
        userAccountRepository.save(local);

        ldapBindingAppService.bindLdapIdentity(local.getId(), "grace", "grace123");

        assertThat(identityBindingRepository.findByProviderCodeAndSubject("ldap", directoryEntryUuid("grace")))
            .as("binding is persisted for the LDAP subject")
            .isPresent()
            .get()
            .extracting(IdentityBinding::getUserId)
            .isEqualTo(local.getId());

        PlatformPrincipal principal = localAuthService.login("grace", "grace123");
        assertThat(principal.userId()).isEqualTo(local.getId());
        assertThat(principal.displayName()).isEqualTo("Grace Smith");
    }

    private static String directoryEntryUuid(String uid) throws Exception {
        ExecResult r = LDAP.execInContainer("ldapsearch", "-x", "-H", "ldap://localhost",
            "-b", "uid=" + uid + "," + BASE_DN, "-s", "base",
            "-D", BIND_DN, "-w", BIND_PASSWORD, "(objectClass=*)", "entryUUID");
        assertThat(r.getExitCode())
            .as("ldapsearch failed: %s", r.getStdout() + r.getStderr())
            .isZero();
        return r.getStdout().lines()
            .filter(line -> line.startsWith("entryUUID:"))
            .map(line -> line.substring("entryUUID:".length()).trim())
            .findFirst()
            .orElseThrow(() -> new AssertionError("entryUUID not returned by ldapsearch"));
    }
}
