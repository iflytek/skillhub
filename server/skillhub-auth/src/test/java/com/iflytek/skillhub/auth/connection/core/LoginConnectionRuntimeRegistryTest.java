package com.iflytek.skillhub.auth.connection.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginConnectionRuntimeRegistryTest {

    @Test
    void activate_atomicallyPublishesACompleteImmutableRevision() throws InterruptedException {
        AdapterDescriptor installed = descriptor(1, 1, 1);
        LoginConnectionRuntimeRegistry registry = registry(installed);
        registry.activate(revision(1, descriptor(1, 0, 1), new TestConfig("issuer-v1", "client-v1")));
        LoginConnectionRevision<TestConfig> next = revision(
                2,
                installed,
                new TestConfig("issuer-v2", "client-v2")
        );
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<LoginConnectionRuntimeSnapshot<?>> observed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> threadFailures = new ConcurrentLinkedQueue<>();
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                await(start);
                for (int index = 0; index < 10_000; index++) {
                    observed.add(registry.requireActive(handle()));
                }
            } catch (Throwable failure) {
                threadFailures.add(failure);
            }
        });
        Thread writer = Thread.ofPlatform().start(() -> {
            try {
                await(start);
                registry.activate(next);
            } catch (Throwable failure) {
                threadFailures.add(failure);
            }
        });

        start.countDown();
        reader.join();
        writer.join();

        assertThat(threadFailures).isEmpty();
        assertThat(observed).isNotEmpty().allSatisfy(snapshot -> {
            assertThat(snapshot.revision()).isIn(1L, 2L);
            TestConfig config = (TestConfig) snapshot.config();
            if (snapshot.revision() == 1L) {
                assertThat(config).isEqualTo(new TestConfig("issuer-v1", "client-v1"));
            } else {
                assertThat(config).isEqualTo(new TestConfig("issuer-v2", "client-v2"));
            }
        });
        assertThat(registry.requireActive(handle()).revision()).isEqualTo(2L);
    }

    @Test
    void inFlightSnapshotRemainsStableAfterAnotherRevisionIsActivated() {
        AdapterDescriptor installed = descriptor(1, 1, 1);
        LoginConnectionRuntimeRegistry registry = registry(installed);
        registry.activate(revision(1, descriptor(1, 0, 1), new TestConfig("issuer-v1", "client-v1")));
        LoginConnectionRuntimeSnapshot<?> inFlight = registry.requireActive(handle());

        registry.activate(revision(2, installed, new TestConfig("issuer-v2", "client-v2")));

        assertThat(inFlight.revision()).isEqualTo(1L);
        assertThat(inFlight.config()).isEqualTo(new TestConfig("issuer-v1", "client-v1"));
        assertThat(registry.requireActive(handle()).revision()).isEqualTo(2L);
    }

    @Test
    void rollback_switchesToAnOlderCompatibleRevisionWithoutMutatingEitherSnapshot() {
        AdapterDescriptor installed = descriptor(1, 2, 1);
        LoginConnectionRuntimeRegistry registry = registry(installed);
        LoginConnectionRevision<TestConfig> first = revision(
                1,
                descriptor(1, 0, 1),
                new TestConfig("issuer-v1", "client-v1")
        );
        LoginConnectionRevision<TestConfig> second = revision(
                2,
                installed,
                new TestConfig("issuer-v2", "client-v2")
        );
        registry.activate(first);
        registry.activate(second);
        LoginConnectionRuntimeSnapshot<?> beforeRollback = registry.requireActive(handle());

        LoginConnectionRuntimeSnapshot<?> rolledBack = registry.rollback(first);

        assertThat(rolledBack.revision()).isEqualTo(1L);
        assertThat(registry.requireActive(handle())).isSameAs(rolledBack);
        assertThat(beforeRollback.revision()).isEqualTo(2L);
        assertThat(beforeRollback.config()).isEqualTo(new TestConfig("issuer-v2", "client-v2"));
    }

    @Test
    void rollback_rejectsUnsupportedConfigSchemaAndKeepsCurrentRevisionActive() {
        AdapterDescriptor installed = descriptor(1, 1, 2);
        LoginConnectionRuntimeRegistry registry = registry(installed);
        LoginConnectionRevision<TestConfig> current = revision(
                2,
                installed,
                new TestConfig("issuer-v2", "client-v2")
        );
        LoginConnectionRevision<TestConfig> incompatible = revision(
                1,
                descriptor(1, 0, 1),
                new TestConfig("issuer-v1", "client-v1")
        );
        registry.activate(current);

        assertThatThrownBy(() -> registry.rollback(incompatible))
                .isInstanceOfSatisfying(
                        ConnectionRevisionIncompatibleException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(ConnectionRevisionIncompatibility.CONFIG_SCHEMA_UNSUPPORTED)
                );
        assertThat(registry.requireActive(handle()).revision()).isEqualTo(2L);
    }

    @Test
    void rollback_rejectsRevisionFromAnotherConnectionAndKeepsCurrentRevisionActive() {
        AdapterDescriptor installed = descriptor(1, 1, 1);
        LoginConnectionRuntimeRegistry registry = registry(installed);
        registry.activate(revision(2, installed, new TestConfig("issuer-v2", "client-v2")));
        LoginConnectionRevision<TestConfig> foreign = new LoginConnectionRevision<>(
                Optional.of("org_1"),
                "connection_2",
                handle(),
                1,
                installed,
                new TestConfig("foreign", "foreign"),
                Instant.parse("2026-09-07T13:00:00Z")
        );

        assertThatThrownBy(() -> registry.rollback(foreign))
                .isInstanceOfSatisfying(
                        ConnectionRevisionIncompatibleException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(ConnectionRevisionIncompatibility.CONNECTION_IDENTITY_MISMATCH)
                );
        assertThat(registry.requireActive(handle()).revision()).isEqualTo(2L);
    }

    @Test
    void controlPlaneDraftCannotBeReadThroughDataPlaneRegistryContract() {
        LoginConnectionDraft<TestDraftConfig> draft = new LoginConnectionDraft<>(
                Optional.of("org_1"),
                "connection_1",
                handle(),
                new AdapterKey("test-redirect"),
                1,
                new TestDraftConfig("unvalidated-value")
        );

        assertThat(draft.editableConfig().value()).isEqualTo("unvalidated-value");
        assertThat(EnterpriseConnectionRegistry.class.getDeclaredMethods())
                .allSatisfy(method -> assertThat(List.of(method.getParameterTypes()))
                        .doesNotContain(LoginConnectionDraft.class));
        assertThat(LoginConnectionActivationControl.class.getDeclaredMethods())
                .allSatisfy(method -> assertThat(List.of(method.getParameterTypes()))
                        .doesNotContain(LoginConnectionDraft.class));
    }

    private static LoginConnectionRuntimeRegistry registry(AdapterDescriptor installed) {
        return new LoginConnectionRuntimeRegistry(new AdapterDescriptorRegistry(List.of(installed)));
    }

    private static LoginConnectionRevision<TestConfig> revision(
            long revision,
            AdapterDescriptor descriptor,
            TestConfig config
    ) {
        return new LoginConnectionRevision<>(
                Optional.of("org_1"),
                "connection_1",
                handle(),
                revision,
                descriptor,
                config,
                Instant.parse("2026-09-07T12:00:00Z").plusSeconds(revision)
        );
    }

    private static AdapterDescriptor descriptor(int major, int minor, int configSchemaVersion) {
        return new AdapterDescriptor(
                new AdapterKey("test-redirect"),
                new AdapterContractVersion(major, minor),
                ConnectionKind.LOGIN,
                configSchemaVersion,
                Optional.of(InteractionModel.REDIRECT),
                Set.of(
                        AdapterCapability.IDENTITY_ASSERTION,
                        AdapterCapability.VERIFIED_EMAIL_ASSERTION
                )
        );
    }

    private static ConnectionHandle handle() {
        return new ConnectionHandle("enterprise-login-a7f9");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while coordinating registry test", interrupted);
        }
    }

    private record TestDraftConfig(String value) {
    }

    private record TestConfig(String issuer, String clientId) implements LoginConnectionRuntimeConfig {
    }
}
