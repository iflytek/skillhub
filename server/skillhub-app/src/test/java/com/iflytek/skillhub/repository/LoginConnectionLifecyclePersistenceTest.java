package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.connection.control.JpaLoginConnectionRepositoryAdapter;
import com.iflytek.skillhub.auth.connection.control.JpaLoginConnectionRevisionRepositoryAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionLifecycleService;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.control.PersistentEnterpriseConnectionRegistry;
import com.iflytek.skillhub.auth.connection.control.StoredLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
import com.iflytek.skillhub.auth.connection.core.AdapterCapability;
import com.iflytek.skillhub.auth.connection.core.AdapterContractVersion;
import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.core.ConnectionKind;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import com.iflytek.skillhub.auth.federation.adapter.TransactionSuspendingRemoteIdentityIoExecutor;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        JpaLoginConnectionRevisionRepositoryAdapter.class,
        LoginConnectionLifecycleService.class,
        TransactionSuspendingRemoteIdentityIoExecutor.class
})
class LoginConnectionLifecyclePersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T05:00:00Z");

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
    private LoginConnectionRevisionRepository revisionRepository;

    @Autowired
    private LoginConnectionLifecycleService lifecycleService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String organizationId;
    private String actorId;
    private LoginConnection connection;
    private StoredLoginConnectionRevision firstRevision;
    private PersistentEnterpriseConnectionRegistry dataPlaneRegistry;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        actorId = "identity-admin-" + unique;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> {
            UserAccount actor = new UserAccount(
                    actorId,
                    "Identity Admin",
                    unique + "@example.com",
                    null
            );
            entityManager.persist(actor);
            Organization organization = Organization.create(
                    "connection-" + unique,
                    "Connection Organization",
                    actor.getId(),
                    NOW
            );
            entityManager.persist(organization);
            organizationId = organization.getId();
        });
        connection = connectionRepository.save(LoginConnection.createOrganization(
                organizationId,
                "Corporate login",
                new AdapterKey("test-redirect"),
                actorId,
                NOW
        ));
        firstRevision = revisionRepository.save(StoredLoginConnectionRevision.create(
                connection.getId(),
                1,
                "1.0",
                1,
                "[\"IDENTITY_ASSERTION\"]",
                "{\"issuer\":\"https://id.example.com\"}",
                null,
                actorId,
                NOW.plusSeconds(1)
        ));
        dataPlaneRegistry = new PersistentEnterpriseConnectionRegistry(
                connectionRepository,
                revisionRepository,
                revision -> new LoginConnectionRuntimeSnapshot<>(
                        revision.organizationId(),
                        revision.connectionId(),
                        revision.publicHandle(),
                        revision.revision(),
                        descriptor(),
                        new TestRuntimeConfig(revision.typedConfigJson())
                )
        );
    }

    @Test
    void onlySuccessfulTestFollowedByExplicitActivationMakesRevisionStartable() {
        assertThatThrownBy(() -> dataPlaneRegistry.requireActive(connection.getPublicHandle()))
                .isInstanceOf(ConnectionUnavailableException.class);

        AtomicBoolean probeCalled = new AtomicBoolean();
        lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                candidate -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    assertThat(candidate.connectionId()).isEqualTo(connection.getId());
                    assertThat(candidate.typedConfigJson())
                            .contains("\"issuer\"", "https://id.example.com");
                    probeCalled.set(true);
                },
                NOW.plusSeconds(10)
        );

        assertThat(probeCalled).isTrue();
        assertThat(connectionRepository.findByOrganizationIdAndId(
                organizationId,
                connection.getId()
        )).get().satisfies(reloaded -> {
            assertThat(reloaded.getStatus()).isEqualTo(LoginConnectionStatus.DRAFT);
            assertThat(reloaded.getLastTestedRevisionId()).contains(firstRevision.getId());
            assertThat(reloaded.getActiveRevisionId()).isEmpty();
        });
        assertThatThrownBy(() -> dataPlaneRegistry.requireActive(connection.getPublicHandle()))
                .isInstanceOf(ConnectionUnavailableException.class);

        lifecycleService.activate(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                NOW.plusSeconds(20)
        );

        assertThat(dataPlaneRegistry.requireActive(connection.getPublicHandle()))
                .satisfies(snapshot -> {
            assertThat(snapshot.connectionId()).isEqualTo(connection.getId());
            assertThat(snapshot.revision()).isEqualTo(1);
            assertThat(snapshot.adapterKey()).isEqualTo(new AdapterKey("test-redirect"));
        });

        lifecycleService.suspend(
                organizationId,
                connection.getId(),
                NOW.plusSeconds(30)
        );
        assertThatThrownBy(() -> dataPlaneRegistry.requireActive(connection.getPublicHandle()))
                .isInstanceOf(ConnectionUnavailableException.class);
    }

    @Test
    void failedProbeDoesNotChangeTestedOrActivePointers() {
        assertThatThrownBy(() -> lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                candidate -> {
                    throw new IllegalStateException("simulated safe probe failure");
                },
                NOW.plusSeconds(10)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated safe probe failure");

        assertThat(connectionRepository.findByOrganizationIdAndId(
                organizationId,
                connection.getId()
        )).get().satisfies(reloaded -> {
            assertThat(reloaded.getStatus()).isEqualTo(LoginConnectionStatus.DRAFT);
            assertThat(reloaded.getLastTestedRevisionId()).isEmpty();
            assertThat(reloaded.getActiveRevisionId()).isEmpty();
        });
    }

    @Test
    void concurrentDisableDuringProbeWinsOverSuccessfulRemoteResult() {
        assertThatThrownBy(() -> lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                candidate -> lifecycleService.disable(
                        organizationId,
                        connection.getId(),
                        NOW.plusSeconds(10)
                ),
                NOW.plusSeconds(20)
        )).hasMessage("error.loginConnection.disabled");

        assertThat(connectionRepository.findByOrganizationIdAndId(
                organizationId,
                connection.getId()
        )).get().satisfies(reloaded -> {
            assertThat(reloaded.getStatus()).isEqualTo(LoginConnectionStatus.DISABLED);
            assertThat(reloaded.getLastTestedRevisionId()).isEmpty();
            assertThat(reloaded.getActiveRevisionId()).isEmpty();
        });
    }

    @Test
    void newTestDoesNotInterruptActiveRevisionAndDisableIsTerminal() {
        lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                candidate -> {
                },
                NOW.plusSeconds(10)
        );
        lifecycleService.activate(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                NOW.plusSeconds(20)
        );
        StoredLoginConnectionRevision secondRevision = revisionRepository.save(
                StoredLoginConnectionRevision.create(
                        connection.getId(),
                        2,
                        "1.0",
                        1,
                        "[\"IDENTITY_ASSERTION\"]",
                        "{\"issuer\":\"https://next.example.com\"}",
                        null,
                        actorId,
                        NOW.plusSeconds(21)
                )
        );

        lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                secondRevision.getId(),
                candidate -> {
                },
                NOW.plusSeconds(30)
        );

        LoginConnectionRuntimeSnapshot<?> inFlight =
                dataPlaneRegistry.requireActive(connection.getPublicHandle());
        assertThat(inFlight.revision()).isEqualTo(1);

        lifecycleService.activate(
                organizationId,
                connection.getId(),
                secondRevision.getId(),
                NOW.plusSeconds(40)
        );
        assertThat(dataPlaneRegistry.requireActive(connection.getPublicHandle()).revision())
                .isEqualTo(2);
        assertThat(inFlight.revision()).isEqualTo(1);

        lifecycleService.disable(
                organizationId,
                connection.getId(),
                NOW.plusSeconds(50)
        );
        assertThatThrownBy(() -> dataPlaneRegistry.requireActive(connection.getPublicHandle()))
                .isInstanceOf(ConnectionUnavailableException.class);
        assertThatThrownBy(() -> lifecycleService.activate(
                organizationId,
                connection.getId(),
                secondRevision.getId(),
                NOW.plusSeconds(60)
        )).hasMessage("error.loginConnection.disabled");
    }

    @Test
    void organizationScopedLookupCannotOperateOnAnotherTenantConnection() {
        assertThatThrownBy(() -> lifecycleService.testRevision(
                "another-organization",
                connection.getId(),
                firstRevision.getId(),
                candidate -> {
                },
                NOW.plusSeconds(10)
        )).hasMessage("error.loginConnection.notFound");

        assertThatThrownBy(() -> dataPlaneRegistry.requireActive(
                new ConnectionHandle("unknown-login-handle")
        )).isInstanceOf(ConnectionUnavailableException.class)
                .hasMessage("Login connection is unavailable");
    }

    @Test
    void dataPlaneRejectsMaterializerThatChangesPersistedRevisionIdentity() {
        lifecycleService.testRevision(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                candidate -> {
                },
                NOW.plusSeconds(10)
        );
        lifecycleService.activate(
                organizationId,
                connection.getId(),
                firstRevision.getId(),
                NOW.plusSeconds(20)
        );
        PersistentEnterpriseConnectionRegistry mismatched =
                new PersistentEnterpriseConnectionRegistry(
                        connectionRepository,
                        revisionRepository,
                        revision -> new LoginConnectionRuntimeSnapshot<>(
                                revision.organizationId(),
                                revision.connectionId(),
                                revision.publicHandle(),
                                revision.revision() + 1,
                                descriptor(),
                                new TestRuntimeConfig(revision.typedConfigJson())
                        )
                );

        assertThatThrownBy(() -> mismatched.requireActive(connection.getPublicHandle()))
                .isInstanceOf(ConnectionUnavailableException.class)
                .hasMessage("Login connection is unavailable");
    }

    private static AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                new AdapterKey("test-redirect"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.LOGIN,
                1,
                Optional.of(InteractionModel.REDIRECT),
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );
    }

    private record TestRuntimeConfig(String typedConfigJson)
            implements LoginConnectionRuntimeConfig {
    }
}
