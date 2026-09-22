package com.iflytek.skillhub.repository;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.connection.control.JpaLoginConnectionRepositoryAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.AesGcmSecretEnvelopeCipher;
import com.iflytek.skillhub.auth.connection.secret.InMemorySecretEnvelopeKeyring;
import com.iflytek.skillhub.auth.connection.secret.JpaLoginConnectionSecretRepositoryAdapter;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretRepository;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretStatus;
import com.iflytek.skillhub.auth.connection.secret.SecretConfigurationSummary;
import com.iflytek.skillhub.auth.connection.secret.SecretEnvelopeCipher;
import com.iflytek.skillhub.auth.connection.secret.SecretEnvelopeKey;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterial;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterialUnavailableException;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.auth.connection.secret.SecretReference;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JpaLoginConnectionRepositoryAdapter.class,
        JpaLoginConnectionSecretRepositoryAdapter.class,
        LoginConnectionSecretService.class,
        LoginConnectionSecretPersistenceTest.SecretCipherConfiguration.class
})
class LoginConnectionSecretPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );
    }

    @Autowired
    private LoginConnectionRepository connectionRepository;

    @Autowired
    private LoginConnectionSecretRepository secretRepository;

    @Autowired
    private LoginConnectionSecretService secretService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String organizationId;
    private String actorId;
    private LoginConnection connection;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        actorId = "secret-admin-" + unique;
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            UserAccount actor = new UserAccount(
                    actorId,
                    "Secret Admin",
                    unique + "@example.com",
                    null
            );
            entityManager.persist(actor);
            Organization organization = Organization.create(
                    "secret-" + unique,
                    "Secret Organization",
                    actor.getId(),
                    NOW
            );
            entityManager.persist(organization);
            organizationId = organization.getId();
        });
        connection = connectionRepository.save(LoginConnection.createOrganization(
                organizationId,
                "Corporate OIDC",
                new AdapterKey("oidc"),
                actorId,
                NOW
        ));
    }

    @Test
    void storesOnlyAuthenticatedCiphertextAndReturnsRedactedMetadata() {
        SecretReference reference = rotate("client-secret-that-must-not-leak", Duration.ZERO, NOW);

        Object[] stored = new TransactionTemplate(transactionManager).execute(status ->
                (Object[]) entityManager.createNativeQuery("""
                        SELECT ciphertext, nonce, key_id
                        FROM login_connection_secret_version
                        WHERE connection_id = :connectionId AND binding_version = 1
                        """)
                        .setParameter("connectionId", connection.getId())
                        .getSingleResult()
        );
        assertThat(new String((byte[]) stored[0], ISO_8859_1))
                .doesNotContain("client-secret-that-must-not-leak");
        assertThat((byte[]) stored[1]).hasSize(12);
        assertThat(stored[2]).isEqualTo("test-master-key");

        try (SecretMaterial resolved = secretService.resolve(
                reference,
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW.plusSeconds(1)
        )) {
            assertThat(read(resolved)).isEqualTo("client-secret-that-must-not-leak");
        }

        SecretConfigurationSummary summary = secretService.describe(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        );
        assertThat(summary.configured()).isTrue();
        assertThat(summary.updatedAt()).isEqualTo(NOW);
        assertThat(summary.previousValidUntil()).isNull();
        assertThat(secretRepository.findAll(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).singleElement().satisfies(secret -> assertThat(secret.toString())
                .contains("envelope=<redacted>")
                .doesNotContain(
                        "client-secret-that-must-not-leak",
                        "test-master-key"
                ));
    }

    @Test
    void rotationKeepsOnePinnedPreviousVersionForTheBoundedWindow() {
        SecretReference first = rotate("first-secret", Duration.ZERO, NOW);
        SecretReference second = rotate(
                "second-secret",
                Duration.ofHours(1),
                NOW.plusSeconds(10)
        );

        assertThat(readResolved(first, NOW.plusSeconds(20))).isEqualTo("first-secret");
        assertThat(readResolved(second, NOW.plusSeconds(20))).isEqualTo("second-secret");

        SecretReference third = rotate(
                "third-secret",
                Duration.ofMinutes(30),
                NOW.plusSeconds(30)
        );

        assertUnavailable(() -> secretService.resolve(
                first,
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW.plusSeconds(31)
        ));
        assertThat(readResolved(second, NOW.plusSeconds(31))).isEqualTo("second-secret");
        assertThat(readResolved(third, NOW.plusSeconds(31))).isEqualTo("third-secret");
        assertUnavailable(() -> secretService.resolve(
                second,
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW.plusSeconds(30).plus(Duration.ofMinutes(30))
        ));

        assertThat(secretRepository.findAll(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).extracting(secret -> secret.getStatus())
                .containsExactly(
                        LoginConnectionSecretStatus.CURRENT,
                        LoginConnectionSecretStatus.RETIRING,
                        LoginConnectionSecretStatus.REVOKED
                );
        assertThat(secretService.describe(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        ).previousValidUntil()).isEqualTo(NOW.plusSeconds(30).plus(Duration.ofMinutes(30)));
    }

    @Test
    void wrongTenantPurposeAndRevokedVersionAllFailWithTheSameSafeError() {
        SecretReference current = rotate("revocable-secret", Duration.ZERO, NOW);

        assertUnavailable(() -> secretService.resolve(
                new SecretReference(
                        "another-organization",
                        connection.getId(),
                        SecretPurpose.LOGIN_CLIENT_SECRET,
                        current.bindingVersion()
                ),
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW.plusSeconds(1)
        ));
        assertUnavailable(() -> secretService.resolve(
                current,
                new SecretPurpose("login.private-key"),
                NOW.plusSeconds(1)
        ));

        secretService.revoke(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET,
                current.bindingVersion(),
                actorId,
                NOW.plusSeconds(2)
        );

        assertUnavailable(() -> secretService.resolve(
                current,
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW.plusSeconds(3)
        ));
        assertThat(secretService.describe(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).isEqualTo(SecretConfigurationSummary.notConfigured());
    }

    @Test
    void rejectsUnboundedRotationWindowBeforeWritingAnything() {
        try (SecretMaterial material = SecretMaterial.copyOf("new-secret".getBytes(UTF_8))) {
            assertThatThrownBy(() -> secretService.rotate(
                    organizationId,
                    connection.getId(),
                    SecretPurpose.LOGIN_CLIENT_SECRET,
                    material,
                    Duration.ofHours(25),
                    actorId,
                    NOW
            )).isInstanceOf(DomainBadRequestException.class)
                    .hasMessage("error.loginConnection.secret.rotation.overlap.invalid");
        }

        assertThat(secretRepository.findAll(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).isEmpty();
    }

    @Test
    void bindingVersionsStayConnectionWideAcrossDifferentPurposes() {
        SecretReference clientSecret = rotate("client-secret", Duration.ZERO, NOW);
        SecretPurpose privateKeyPurpose = new SecretPurpose("login.private-key");
        SecretReference privateKey;
        try (SecretMaterial material = SecretMaterial.copyOf("private-key".getBytes(UTF_8))) {
            privateKey = secretService.rotate(
                    organizationId,
                    connection.getId(),
                    privateKeyPurpose,
                    material,
                    Duration.ZERO,
                    actorId,
                    NOW.plusSeconds(1)
            );
        }

        assertThat(clientSecret.bindingVersion()).isEqualTo(1);
        assertThat(privateKey.bindingVersion()).isEqualTo(2);
        try (SecretMaterial resolved = secretService.resolve(
                privateKey,
                privateKeyPurpose,
                NOW.plusSeconds(2)
        )) {
            assertThat(read(resolved)).isEqualTo("private-key");
        }
    }

    @Test
    void concurrentRotationsAreSerializedByTheConnectionLock() throws Exception {
        rotate("initial-secret", Duration.ZERO, NOW);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return rotate(
                        "concurrent-secret-a",
                        Duration.ofMinutes(10),
                        NOW.plusSeconds(1)
                );
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return rotate(
                        "concurrent-secret-b",
                        Duration.ofMinutes(10),
                        NOW.plusSeconds(1)
                );
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(Set.of(
                    first.get(20, TimeUnit.SECONDS).bindingVersion(),
                    second.get(20, TimeUnit.SECONDS).bindingVersion()
            )).containsExactlyInAnyOrder(2L, 3L);
        }

        assertThat(secretRepository.findAll(
                organizationId,
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).extracting(secret -> secret.getStatus())
                .containsExactly(
                        LoginConnectionSecretStatus.CURRENT,
                        LoginConnectionSecretStatus.RETIRING,
                        LoginConnectionSecretStatus.REVOKED
                );
    }

    private SecretReference rotate(String value, Duration overlap, Instant occurredAt) {
        try (SecretMaterial material = SecretMaterial.copyOf(value.getBytes(UTF_8))) {
            return secretService.rotate(
                    organizationId,
                    connection.getId(),
                    SecretPurpose.LOGIN_CLIENT_SECRET,
                    material,
                    overlap,
                    actorId,
                    occurredAt
            );
        }
    }

    private String readResolved(SecretReference reference, Instant now) {
        try (SecretMaterial resolved = secretService.resolve(
                reference,
                SecretPurpose.LOGIN_CLIENT_SECRET,
                now
        )) {
            return read(resolved);
        }
    }

    private static String read(SecretMaterial material) {
        return material.use(bytes -> new String(bytes, UTF_8));
    }

    private static void assertUnavailable(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(SecretMaterialUnavailableException.class)
                .hasMessage("Secret material is unavailable")
                .hasNoCause();
    }

    @TestConfiguration
    static class SecretCipherConfiguration {

        @Bean(destroyMethod = "close")
        InMemorySecretEnvelopeKeyring secretEnvelopeKeyring() {
            byte[] bytes = new byte[32];
            Arrays.fill(bytes, (byte) 5);
            SecretEnvelopeKey key = new SecretEnvelopeKey("test-master-key", bytes);
            Arrays.fill(bytes, (byte) 0);
            try (key) {
                return new InMemorySecretEnvelopeKeyring(key);
            }
        }

        @Bean
        SecretEnvelopeCipher secretEnvelopeCipher(
                InMemorySecretEnvelopeKeyring keyring
        ) {
            return new AesGcmSecretEnvelopeCipher(keyring);
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
