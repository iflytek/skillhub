package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.connection.control.BuiltInLoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotDecoder;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.oidc.DefaultOidcMetadataSource;
import com.iflytek.skillhub.auth.connection.oidc.JdkOidcSingleHopHttpTransport;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionMetadataProbe;
import com.iflytek.skillhub.auth.connection.oidc.OidcHostAddressResolver;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.oidc.OidcMetadataManager;
import com.iflytek.skillhub.auth.connection.oidc.OidcOutboundTargetPolicy;
import com.iflytek.skillhub.auth.connection.oidc.SecureOidcDocumentClient;
import com.iflytek.skillhub.auth.connection.secret.AesGcmSecretEnvelopeCipher;
import com.iflytek.skillhub.auth.connection.secret.InMemorySecretEnvelopeKeyring;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretRepository;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** R1-B composition root for enterprise OIDC control-plane management only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "skillhub.enterprise.oidc.enabled", havingValue = "true")
public class EnterpriseOidcRuntimeConfiguration {

    @Bean(destroyMethod = "close")
    InMemorySecretEnvelopeKeyring enterpriseSecretEnvelopeKeyring(
            EnterpriseSecretEnvelopeProperties properties
    ) {
        return properties.createKeyring();
    }

    @Bean
    LoginConnectionSecretService loginConnectionSecretService(
            com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository connections,
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
}
