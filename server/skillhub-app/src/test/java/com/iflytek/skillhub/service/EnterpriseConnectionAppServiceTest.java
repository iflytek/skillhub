package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionConfigurationInput;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionLifecycleService;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestException;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestProbe;
import com.iflytek.skillhub.auth.connection.control.PreparedLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.auth.operation.IdentityOperation;
import com.iflytek.skillhub.auth.operation.IdentityOperationRepository;
import com.iflytek.skillhub.auth.operation.IdentityOperationStatus;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.audit.OrganizationAuditResult;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.dto.LoginConnectionTestResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseConnectionQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class EnterpriseConnectionAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void failedDraftTestIsPersistedAsSafeHealthWithoutChangingLifecycle() {
        OrganizationAuthorizationService authorization = mock(OrganizationAuthorizationService.class);
        LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
        LoginConnectionRevisionRepository revisions = mock(LoginConnectionRevisionRepository.class);
        LoginConnectionLifecycleService lifecycle = mock(LoginConnectionLifecycleService.class);
        IdentityOperationRepository operations = mock(IdentityOperationRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        LoginConnectionSecretService secrets = mock(LoginConnectionSecretService.class);
        LoginConnection connection = LoginConnection.createOrganization(
                "org-1",
                "Corporate SSO",
                new AdapterKey("oidc"),
                "identity-admin",
                NOW.minusSeconds(60)
        );
        given(connections.findByOrganizationIdAndId("org-1", connection.getId()))
                .willReturn(Optional.of(connection));
        LoginConnectionControlAdapter adapter = new LoginConnectionControlAdapter() {
            @Override
            public AdapterKey adapterKey() {
                return new AdapterKey("oidc");
            }

            @Override
            public Optional<SecretPurpose> secretPurpose() {
                return Optional.of(SecretPurpose.LOGIN_CLIENT_SECRET);
            }

            @Override
            public PreparedLoginConnectionRevision prepare(
                    LoginConnectionConfigurationInput configuration
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LoginConnectionTestProbe testProbe() {
                return ignored -> {
                    throw new LoginConnectionTestException(
                            LoginConnectionTestFailureReason.METADATA
                    );
                };
            }
        };
        given(lifecycle.testRevision(
                org.mockito.ArgumentMatchers.eq("org-1"),
                org.mockito.ArgumentMatchers.eq(connection.getId()),
                org.mockito.ArgumentMatchers.eq("revision-2"),
                any(),
                org.mockito.ArgumentMatchers.eq(NOW)
        )).willAnswer(invocation -> {
            LoginConnectionTestProbe probe = invocation.getArgument(3);
            probe.verify(null);
            return null;
        });
        RequestIdAccessor requestIds = new RequestIdAccessor();
        EnterpriseConnectionAppService service = new EnterpriseConnectionAppService(
                authorization,
                connections,
                revisions,
                lifecycle,
                operations,
                auditLogService,
                mock(EnterpriseConnectionQueryRepository.class),
                provider(LoginConnectionControlAdapterRegistry.class,
                        new LoginConnectionControlAdapterRegistry(List.of(adapter))),
                provider(LoginConnectionSecretService.class, secrets),
                requestIds,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        LoginConnectionTestResponse response;
        try (RequestIdAccessor.Scope ignored = requestIds.open("request-123")) {
            response = service.testRevision(
                    "org-1",
                    connection.getId(),
                    "revision-2",
                    "identity-admin"
            );
        }

        assertThat(response.success()).isFalse();
        assertThat(response.failureReason()).isEqualTo("METADATA");
        assertThat(connection.getStatus().name()).isEqualTo("DRAFT");
        assertThat(connection.getActiveRevisionId()).isEmpty();
        assertThat(connection.getLastTestedRevisionId()).isEmpty();
        ArgumentCaptor<IdentityOperation> operation = ArgumentCaptor.forClass(
                IdentityOperation.class
        );
        verify(operations).save(operation.capture());
        assertThat(operation.getValue().getStatus()).isEqualTo(IdentityOperationStatus.FAILED);
        assertThat(operation.getValue().getRevisionId()).isEqualTo("revision-2");
        assertThat(operation.getValue().getRequestId()).contains("request-123");
        assertThat(operation.getValue().getErrorCode()).contains("METADATA");
        ArgumentCaptor<OrganizationAuditEvent> audit = ArgumentCaptor.forClass(
                OrganizationAuditEvent.class
        );
        verify(auditLogService).record(audit.capture());
        assertThat(audit.getValue().action())
                .isEqualTo(OrganizationAuditAction.LOGIN_CONNECTION_TEST_FAILED);
        assertThat(audit.getValue().result()).isEqualTo(OrganizationAuditResult.FAILED);
        assertThat(audit.getValue().requestId()).isEqualTo("request-123");
        assertThat(audit.getValue().detail().toJson())
                .contains("METADATA")
                .doesNotContainIgnoringCase("secret", "token", "exception");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }
}
