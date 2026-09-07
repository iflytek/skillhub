package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.EnterpriseConnectionRegistry;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RedirectAuthenticationAdapterContractTest {

    @Test
    void redirectAdapterProducesVerifiedAssertionThroughTwoPhaseContract() {
        ConnectionHandle handle = new ConnectionHandle("enterprise-login-a7f9");
        LoginConnectionRuntimeSnapshot<TestRuntimeConfig> snapshot = new LoginConnectionRuntimeSnapshot<>(
                Optional.of("org_1"),
                "connection_1",
                handle,
                3L,
                new AdapterKey("test-redirect"),
                1,
                1,
                InteractionModel.REDIRECT,
                new TestRuntimeConfig("https://identity.example.com")
        );
        EnterpriseConnectionRegistry registry = requested -> {
            if (!handle.equals(requested)) {
                throw new ConnectionUnavailableException();
            }
            return snapshot;
        };
        RedirectAuthenticationAdapter<TestRuntimeConfig> adapter = new TestRedirectAdapter();

        LoginConnectionRuntimeSnapshot<?> resolved = registry.requireActive(handle);
        RedirectStartResult started = adapter.start(
                snapshot,
                new RedirectStartRequest(
                        "browser-transaction-1",
                        URI.create("https://skillhub.example.com/login/callback"),
                        Optional.of("/dashboard")
                )
        );
        IdentityAssertion assertion = adapter.complete(
                snapshot,
                new RedirectCompleteRequest(
                        "browser-transaction-1",
                        URI.create("https://skillhub.example.com/login/callback?code=test&state=opaque")
                )
        );

        assertThat(resolved.revision()).isEqualTo(3L);
        assertThat(started.authorizationUri()).isEqualTo(
                URI.create("https://identity.example.com/authorize?transaction=browser-transaction-1")
        );
        assertThat(assertion.connectionId()).isEqualTo("connection_1");
        assertThat(assertion.subject().value()).isEqualTo("subject-123");
    }

    @Test
    void registryFailsClosedForUnknownOrInactiveConnection() {
        EnterpriseConnectionRegistry registry = handle -> {
            throw new ConnectionUnavailableException();
        };

        assertThatThrownBy(() -> registry.requireActive(new ConnectionHandle("unknown-handle")))
                .isInstanceOf(ConnectionUnavailableException.class);
    }

    @Test
    void interactionModelsNameDeferredCredentialAndPassiveFamiliesWithoutExpandingRedirectContract() {
        assertThat(InteractionModel.values())
                .containsExactly(
                        InteractionModel.REDIRECT,
                        InteractionModel.CREDENTIAL,
                        InteractionModel.PASSIVE_ASSERTION
                );
        assertThat(RedirectAuthenticationAdapter.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("adapterKey", "configType", "start", "complete");
    }

    @Test
    void adapterContractExposesNoPlatformStateOrRepositoryTypes() {
        Set<String> forbiddenTypeFragments = Set.of(
                "UserAccount",
                "OrganizationMembership",
                "NamespaceMember",
                "PlatformPrincipal",
                "Repository"
        );

        Stream<Class<?>> exposedTypes = Arrays.stream(RedirectAuthenticationAdapter.class.getDeclaredMethods())
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())
                ));

        assertThat(exposedTypes.map(Class::getName))
                .noneMatch(name -> forbiddenTypeFragments.stream().anyMatch(name::contains));
    }

    private record TestRuntimeConfig(String issuer) implements LoginConnectionRuntimeConfig {
    }

    private static final class TestRedirectAdapter implements RedirectAuthenticationAdapter<TestRuntimeConfig> {

        @Override
        public AdapterKey adapterKey() {
            return new AdapterKey("test-redirect");
        }

        @Override
        public Class<TestRuntimeConfig> configType() {
            return TestRuntimeConfig.class;
        }

        @Override
        public RedirectStartResult start(
                LoginConnectionRuntimeSnapshot<TestRuntimeConfig> connection,
                RedirectStartRequest request
        ) {
            return new RedirectStartResult(URI.create(
                    connection.config().issuer() + "/authorize?transaction=" + request.browserTransactionId()
            ));
        }

        @Override
        public IdentityAssertion complete(
                LoginConnectionRuntimeSnapshot<TestRuntimeConfig> connection,
                RedirectCompleteRequest request
        ) {
            return new IdentityAssertion(
                    connection.organizationId(),
                    connection.connectionId(),
                    URI.create(connection.config().issuer()),
                    new SubjectRef(new SubjectType("opaque-user-id"), "subject-123"),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(new Assurance("single-factor")),
                    Instant.parse("2026-09-07T12:00:00Z"),
                    Map.of()
            );
        }
    }
}
