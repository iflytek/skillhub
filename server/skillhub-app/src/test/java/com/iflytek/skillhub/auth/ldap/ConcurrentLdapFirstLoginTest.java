package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Concurrency coverage for LDAP first-login provisioning: two simultaneous first logins for the
 * same LDAP subject must resolve to a single account and a single identity binding. One login
 * wins the {@code (provider_code, subject)} unique-constraint race; the loser re-resolves the
 * existing identity in a fresh transaction and still succeeds.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentLdapFirstLoginTest {

    private static final String BASE_DN = "dc=example,dc=org";
    private static final String BIND_DN = "cn=admin," + BASE_DN;

    @Container
    static final GenericContainer<?> LDAP = new GenericContainer<>("osixia/openldap:1.5.0")
        .withEnv("LDAP_ORGANISATION", "Example Inc")
        .withEnv("LDAP_DOMAIN", "example.org")
        .withEnv("LDAP_ADMIN_PASSWORD", "admin")
        .withExposedPorts(389);

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("skillhub.ldap.enabled", () -> "true");
        registry.add("skillhub.ldap.url", () -> "ldap://" + LDAP.getHost() + ":" + LDAP.getMappedPort(389));
        registry.add("skillhub.ldap.base", () -> BASE_DN);
        registry.add("skillhub.ldap.username", () -> BIND_DN);
        registry.add("skillhub.ldap.password", () -> "admin");
    }

    @Autowired
    private LocalAuthService localAuthService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private IdentityBindingRepository identityBindingRepository;

    // Namespace seeding is unrelated to the concurrency behavior under test.
    @MockBean
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    @BeforeAll
    static void seedDirectory() throws Exception {
        LDAP.copyFileToContainer(MountableFile.forClasspathResource("ldap/seed-users.ldif"), "/tmp/seed-users.ldif");
        awaitLdapReady();
        ExecResult add = LDAP.execInContainer("ldapadd", "-x", "-H", "ldap://localhost",
            "-D", BIND_DN, "-w", "admin", "-f", "/tmp/seed-users.ldif");
        assertThat(add.getExitCode())
            .as("ldapadd failed: %s", add.getStdout() + add.getStderr())
            .isZero();
    }

    private static void awaitLdapReady() throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                ExecResult r = LDAP.execInContainer("ldapsearch", "-x", "-H", "ldap://localhost",
                    "-b", BASE_DN, "-D", BIND_DN, "-w", "admin", "(objectClass=*)", "dn");
                if (r.getExitCode() == 0) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("OpenLDAP did not become ready");
    }

    @Test
    void concurrentFirstLogin_sameSubject_singleAccountAndBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<PlatformPrincipal> login = () -> localAuthService.login("alice", "alice123");
            Future<PlatformPrincipal> first = pool.submit(login);
            Future<PlatformPrincipal> second = pool.submit(login);

            PlatformPrincipal r1 = first.get();
            PlatformPrincipal r2 = second.get();

            // Both concurrent first logins must succeed and resolve to the same account;
            // exactly one identity binding and one account exist afterwards.
            assertThat(r1.userId()).isEqualTo(r2.userId());
            assertThat(identityBindingRepository.findAll()).hasSize(1);
            assertThat(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }
}
