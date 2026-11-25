package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.secret.EnterpriseSecretEnvelopeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EnterpriseOidcProperties.class, EnterpriseSecretEnvelopeProperties.class})
class EnterpriseOidcControlConfiguration {

    @Bean(destroyMethod = "close")
    OidcHttpDocumentClient oidcHttpDocumentClient(EnterpriseOidcProperties configuration) {
        return new OidcHttpDocumentClient(configuration);
    }

    @Bean
    OidcConnectionProbe oidcConnectionProbe(OidcHttpDocumentClient client, ObjectMapper mapper) {
        return new OidcConnectionProbe(client::get, mapper);
    }
}
