package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociationService;
import com.iflytek.skillhub.auth.federation.association.PreProvisionedLoginSubjectRepository;
import com.iflytek.skillhub.auth.federation.core.EnterpriseIdentitySecurityEventRecorder;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import com.iflytek.skillhub.auth.federation.migration.IdentityBindingReadMetrics;
import com.iflytek.skillhub.auth.federation.migration.LegacyIdentityBindingDualReader;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds conservative rollout controls and enables enterprise association only for the OIDC pilot. */
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

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.enterprise", name = "oidc-login-enabled", havingValue = "true")
    EnterpriseIdentityAssociationService enterpriseIdentityAssociationService(
            IdentityCoreActivation activation,
            ExternalIdentityRepository externalIdentities,
            PreProvisionedLoginSubjectRepository preProvisionedSubjects,
            OrganizationDomainRepository domains,
            OrganizationMembershipRepository memberships,
            OrganizationRepository organizations,
            UserAccountRepository accounts,
            GlobalNamespaceMembershipService globalMemberships,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            EnterpriseIdentitySecurityEventRecorder securityEvents
    ) {
        return new EnterpriseIdentityAssociationService(activation, externalIdentities, preProvisionedSubjects,
                domains, memberships, organizations, accounts, globalMemberships, events, transactionManager,
                clock, securityEvents);
    }
}
