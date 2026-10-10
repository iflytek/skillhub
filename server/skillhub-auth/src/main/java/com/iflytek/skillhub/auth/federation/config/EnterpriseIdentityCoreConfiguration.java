package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.migration.IdentityBindingReadMetrics;
import com.iflytek.skillhub.auth.federation.migration.LegacyIdentityBindingDualReader;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds conservative rollout controls without enabling the identity decision module itself. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        IdentityCoreProperties.class,
        EnterpriseIdentityRolloutProperties.class
})
public class EnterpriseIdentityCoreConfiguration {

    @Bean
    IdentityCoreActivation identityCoreActivation(
            IdentityCoreProperties core,
            EnterpriseIdentityRolloutProperties rollout
    ) {
        return new ConfiguredIdentityCoreActivation(core, rollout);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityBindingReadMetrics.class)
    IdentityBindingReadMetrics identityBindingReadMetrics() {
        return IdentityBindingReadMetrics.noop();
    }

    @Bean
    LegacyIdentityBindingDualReader legacyIdentityBindingDualReader(
            ExternalIdentityRepository externalIdentities,
            IdentityBindingRepository legacyBindings,
            IdentityBindingReadMetrics metrics
    ) {
        return new LegacyIdentityBindingDualReader(externalIdentities, legacyBindings, metrics);
    }
}
