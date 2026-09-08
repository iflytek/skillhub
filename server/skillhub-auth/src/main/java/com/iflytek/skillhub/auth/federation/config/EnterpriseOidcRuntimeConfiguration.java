package com.iflytek.skillhub.auth.federation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.control.BuiltInLoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotDecoder;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.control.PersistentEnterpriseConnectionRegistry;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.EnterpriseConnectionContinuationRegistry;
import com.iflytek.skillhub.auth.connection.oidc.DefaultOidcMetadataSource;
import com.iflytek.skillhub.auth.connection.oidc.JdkOidcSingleHopHttpTransport;
import com.iflytek.skillhub.auth.connection.oidc.OidcAuthorizationTransactionService;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionMetadataProbe;
import com.iflytek.skillhub.auth.connection.oidc.OidcAuthorizationTransactionStore;
import com.iflytek.skillhub.auth.connection.oidc.OidcHostAddressResolver;
import com.iflytek.skillhub.auth.connection.oidc.OidcIdTokenValidator;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.oidc.OidcMetadataManager;
import com.iflytek.skillhub.auth.connection.oidc.OidcOutboundTargetPolicy;
import com.iflytek.skillhub.auth.connection.oidc.OidcRedirectAuthenticationAdapter;
import com.iflytek.skillhub.auth.connection.oidc.OidcTokenEndpointClient;
import com.iflytek.skillhub.auth.connection.oidc.RedisOidcAuthorizationTransactionStore;
import com.iflytek.skillhub.auth.connection.oidc.SecureOidcDocumentClient;
import com.iflytek.skillhub.auth.connection.secret.AesGcmSecretEnvelopeCipher;
import com.iflytek.skillhub.auth.connection.secret.InMemorySecretEnvelopeKeyring;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretRepository;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.federation.EnterpriseRedirectLoginGateway;
import com.iflytek.skillhub.auth.federation.EnterpriseRedirectLoginService;
import com.iflytek.skillhub.auth.federation.adapter.BuiltInRedirectAuthenticationAdapterRegistry;
import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociationService;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.association.PreProvisionedLoginSubjectRepository;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.core.RedirectAuthenticationAdapter;
import com.iflytek.skillhub.auth.federation.core.RedirectCompleteRequest;
import com.iflytek.skillhub.auth.federation.core.RedirectStartRequest;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.auth.session.EnterpriseBrowserSessionService;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Production composition root for the first built-in dynamic enterprise login Adapter. */
@Configuration(proxyBeanMethods = false)
public class EnterpriseOidcRuntimeConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "skillhub.enterprise.oidc.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    EnterpriseRedirectLoginGateway disabledEnterpriseRedirectLoginGateway() {
        return new DisabledEnterpriseRedirectLoginGateway();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "skillhub.enterprise.oidc.enabled", havingValue = "true")
    static class EnabledRuntime {

        @Bean(destroyMethod = "close")
        InMemorySecretEnvelopeKeyring enterpriseSecretEnvelopeKeyring(
                EnterpriseSecretEnvelopeProperties properties
        ) {
            return properties.createKeyring();
        }

        @Bean
        LoginConnectionSecretService loginConnectionSecretService(
                LoginConnectionRepository connections,
                LoginConnectionSecretRepository secrets,
                InMemorySecretEnvelopeKeyring keyring
        ) {
            return new LoginConnectionSecretService(
                    connections,
                    secrets,
                    new AesGcmSecretEnvelopeCipher(keyring)
            );
        }

        @Bean
        OidcOutboundTargetPolicy oidcOutboundTargetPolicy(EnterpriseOidcProperties properties) {
            properties.validateEnabledConfiguration();
            return new OidcOutboundTargetPolicy(
                    OidcHostAddressResolver.system(),
                    properties.getPrivateHostAllowlist()
            );
        }

        @Bean
        JdkOidcSingleHopHttpTransport oidcHttpTransport() {
            return new JdkOidcSingleHopHttpTransport();
        }

        @Bean
        OidcMetadataManager oidcMetadataManager(
                OidcOutboundTargetPolicy targetPolicy,
                JdkOidcSingleHopHttpTransport transport,
                RemoteIdentityIoExecutor remoteIdentityIo
        ) {
            SecureOidcDocumentClient documents = new SecureOidcDocumentClient(
                    targetPolicy,
                    transport
            );
            return new OidcMetadataManager(
                    new DefaultOidcMetadataSource(documents, targetPolicy),
                    remoteIdentityIo
            );
        }

        @Bean
        OidcAuthorizationTransactionStore oidcAuthorizationTransactionStore(
                StringRedisTemplate redis,
                ObjectMapper objectMapper
        ) {
            return new RedisOidcAuthorizationTransactionStore(redis, objectMapper);
        }

        @Bean
        OidcAuthorizationTransactionService oidcAuthorizationTransactionService(
                OidcMetadataManager metadata,
                OidcAuthorizationTransactionStore transactions
        ) {
            return new OidcAuthorizationTransactionService(metadata, transactions);
        }

        @Bean
        OidcLoginConnectionRuntimeSnapshotMaterializer oidcRuntimeSnapshotDecoder() {
            return new OidcLoginConnectionRuntimeSnapshotMaterializer();
        }

        @Bean
        LoginConnectionRuntimeSnapshotMaterializer loginConnectionRuntimeSnapshotMaterializer(
                List<LoginConnectionRuntimeSnapshotDecoder> decoders
        ) {
            return new BuiltInLoginConnectionRuntimeSnapshotMaterializer(decoders);
        }

        @Bean
        OidcConnectionMetadataProbe oidcConnectionMetadataProbe(OidcMetadataManager metadata) {
            return (key, issuer, occurredAt) -> metadata.get(key, issuer, occurredAt);
        }

        @Bean
        OidcLoginConnectionControlAdapter oidcLoginConnectionControlAdapter(
                LoginConnectionRuntimeSnapshotMaterializer materializer,
                LoginConnectionSecretService secrets,
                OidcConnectionMetadataProbe metadata,
                Clock clock
        ) {
            return new OidcLoginConnectionControlAdapter(
                    materializer,
                    secrets,
                    metadata,
                    clock
            );
        }

        @Bean
        LoginConnectionControlAdapterRegistry loginConnectionControlAdapterRegistry(
                List<LoginConnectionControlAdapter> adapters
        ) {
            return new LoginConnectionControlAdapterRegistry(adapters);
        }

        @Bean
        EnterpriseConnectionContinuationRegistry enterpriseConnectionRegistry(
                LoginConnectionRepository connections,
                LoginConnectionRevisionRepository revisions,
                LoginConnectionRuntimeSnapshotMaterializer materializer,
                OrganizationRepository organizations
        ) {
            return new PersistentEnterpriseConnectionRegistry(
                    connections,
                    revisions,
                    materializer,
                    organizations
            );
        }

        @Bean
        OidcRedirectAuthenticationAdapter oidcRedirectAuthenticationAdapter(
                OidcAuthorizationTransactionService transactions,
                OidcMetadataManager metadata,
                OidcOutboundTargetPolicy targetPolicy,
                JdkOidcSingleHopHttpTransport transport,
                LoginConnectionSecretService secrets,
                RemoteIdentityIoExecutor remoteIdentityIo,
                EnterpriseConnectionContinuationRegistry connections,
                Clock clock
        ) {
            OidcTokenEndpointClient tokenEndpoint = new OidcTokenEndpointClient(
                    targetPolicy,
                    transport,
                    secrets,
                    remoteIdentityIo
            );
            return new OidcRedirectAuthenticationAdapter(
                    transactions,
                    metadata,
                    tokenEndpoint,
                    new OidcIdTokenValidator(metadata),
                    connections,
                    clock
            );
        }

        @Bean
        BuiltInRedirectAuthenticationAdapterRegistry redirectAuthenticationAdapterRegistry(
                List<RedirectAuthenticationAdapter<?>> adapters
        ) {
            return new BuiltInRedirectAuthenticationAdapterRegistry(adapters);
        }

        @Bean
        EnterpriseIdentityAssociationService enterpriseIdentityAssociationService(
                IdentityCoreActivation activation,
                ExternalIdentityRepository externalIdentities,
                PreProvisionedLoginSubjectRepository subjects,
                OrganizationDomainRepository domains,
                OrganizationMembershipRepository memberships,
                OrganizationRepository organizations,
                UserAccountRepository accounts,
                GlobalNamespaceMembershipService globalMemberships,
                ApplicationEventPublisher events,
                PlatformTransactionManager transactionManager,
                Clock clock
        ) {
            return new EnterpriseIdentityAssociationService(
                    activation,
                    externalIdentities,
                    subjects,
                    domains,
                    memberships,
                    organizations,
                    accounts,
                    globalMemberships,
                    events,
                    transactionManager,
                    clock
            );
        }

        @Bean
        EnterpriseRedirectLoginGateway enterpriseRedirectLoginGateway(
                EnterpriseConnectionContinuationRegistry connections,
                BuiltInRedirectAuthenticationAdapterRegistry adapters,
                EnterpriseIdentityAssociationService associations,
                UserAccountRepository accounts,
                RbacService rbac,
                EnterpriseBrowserSessionService sessions,
                EnterpriseIdentityRolloutProperties rollout
        ) {
            return new EnterpriseRedirectLoginService(
                    connections,
                    adapters,
                    associations,
                    accounts,
                    rbac,
                    sessions,
                    rollout
            );
        }
    }

    private static final class DisabledEnterpriseRedirectLoginGateway
            implements EnterpriseRedirectLoginGateway {

        @Override
        public boolean isAvailable(ConnectionHandle handle) {
            return false;
        }

        @Override
        public com.iflytek.skillhub.auth.federation.core.RedirectStartResult start(
                ConnectionHandle handle,
                RedirectStartRequest request
        ) {
            throw new ConnectionUnavailableException();
        }

        @Override
        public LoginCompletion complete(
                ConnectionHandle handle,
                RedirectCompleteRequest request,
                HttpServletRequest httpRequest
        ) {
            throw new ConnectionUnavailableException();
        }
    }
}
