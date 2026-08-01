package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
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

    @Container
    static final GenericContainer<?> LDAP = new GenericContainer<>("osixia/openldap:1.5.0")
        .withEnv("LDAP_ORGANISATION", "Example Inc")
        .withEnv("LDAP_DOMAIN", "example.org")
        .withEnv("LDAP_ADMIN_PASSWORD", BIND_PASSWORD)
        .withExposedPorts(389, 636);

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

    // Local accounts are provisioned through ensureMember(), which requires a built-in global
    // namespace row that the test profile does not seed. The membership side effect is unrelated
    // to the LDAP behavior under test, so it is mocked away.
    @MockBean
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    @BeforeAll
    static void seedDirectory() throws Exception {
        LDAP.copyFileToContainer(MountableFile.forClasspathResource("ldap/seed-users.ldif"), "/tmp/seed-users.ldif");
        LDAP.copyFileToContainer(MountableFile.forClasspathResource("ldap/modify-dave.ldif"), "/tmp/modify-dave.ldif");
        awaitLdapReady();
        ExecResult add = LDAP.execInContainer("ldapadd", "-x", "-H", "ldap://localhost",
            "-D", BIND_DN, "-w", BIND_PASSWORD, "-f", "/tmp/seed-users.ldif");
        assertThat(add.getExitCode())
            .as("ldapadd failed: %s", add.getStdout() + add.getStderr())
            .isZero();
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
    void ldaps_withUntrustedCertificate_returnsTlsError() {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setUrl("ldaps://" + LDAP.getHost() + ":" + LDAP.getMappedPort(636));
        props.setBase(BASE_DN);
        props.setUsername(BIND_DN);
        props.setPassword(BIND_PASSWORD);
        LdapAuthService svc = new LdapAuthService(props,
            mock(UserAccountRepository.class),
            mock(UserRoleBindingRepository.class),
            mock(GlobalNamespaceMembershipService.class),
            mock(IdentityBindingRepository.class),
            mock(EntityManager.class),
            mock(PlatformTransactionManager.class));

        assertThatThrownBy(() -> svc.login("alice", "alice123"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.tlsError");
            });
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
