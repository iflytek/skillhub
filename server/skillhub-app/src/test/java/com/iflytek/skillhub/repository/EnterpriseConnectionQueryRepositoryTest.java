package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
import com.iflytek.skillhub.auth.connection.control.StoredLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.connection.secret.SecretConfigurationSummary;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.auth.operation.IdentityOperation;
import com.iflytek.skillhub.auth.operation.IdentityOperationRepository;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class EnterpriseConnectionQueryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void joinsLatestRevisionSafeSecretSummaryAndBoundedFailureHealth() {
        LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
        LoginConnectionRevisionRepository revisions = mock(LoginConnectionRevisionRepository.class);
        IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
        LoginConnectionSecretService secrets = mock(LoginConnectionSecretService.class);
        LoginConnection connection = LoginConnection.createOrganization(
                "org-1",
                "Corporate SSO",
                new AdapterKey("oidc"),
                "identity-admin",
                NOW.minusSeconds(30)
        );
        StoredLoginConnectionRevision revision = StoredLoginConnectionRevision.create(
                connection.getId(),
                1,
                "1.0",
                1,
                "[\"IDENTITY_ASSERTION\"]",
                "{\"issuer\":\"https://id.example.com\",\"clientId\":\"skillhub\","
                        + "\"scopes\":[\"openid\"]}",
                1L,
                "identity-admin",
                NOW.minusSeconds(20)
        );
        IdentityOperation failed = IdentityOperation.failedLoginConnectionTest(
                "org-1",
                connection.getId(),
                revision.getId(),
                "request-1",
                LoginConnectionTestFailureReason.METADATA,
                NOW
        );
        LoginConnectionControlAdapter adapter = mock(LoginConnectionControlAdapter.class);
        given(adapter.adapterKey()).willReturn(new AdapterKey("oidc"));
        given(adapter.secretPurpose()).willReturn(Optional.of(SecretPurpose.LOGIN_CLIENT_SECRET));
        given(connections.findByOrganizationIdAndId("org-1", connection.getId()))
                .willReturn(Optional.of(connection));
        given(revisions.findLatestByConnectionId(connection.getId()))
                .willReturn(Optional.of(revision));
        given(operations.findLatestLoginConnectionTest("org-1", connection.getId()))
                .willReturn(Optional.of(failed));
        given(secrets.describe(
                "org-1",
                connection.getId(),
                SecretPurpose.LOGIN_CLIENT_SECRET
        )).willReturn(SecretConfigurationSummary.configured(NOW.minusSeconds(10), null));
        EnterpriseConnectionQueryRepository query = new EnterpriseConnectionQueryRepository(
                connections,
                revisions,
                operations,
                provider(
                        LoginConnectionControlAdapterRegistry.class,
                        new LoginConnectionControlAdapterRegistry(List.of(adapter))
                ),
                provider(LoginConnectionSecretService.class, secrets),
                new ObjectMapper()
        );

        LoginConnectionResponse response = query.find("org-1", connection.getId());

        assertThat(response.latestRevision().configuration())
                .containsEntry("issuer", "https://id.example.com")
                .doesNotContainKey("clientSecret");
        assertThat(response.secret().configured()).isTrue();
        assertThat(response.health().status()).isEqualTo("ERROR");
        assertThat(response.health().failureReason()).isEqualTo("METADATA");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }
}
