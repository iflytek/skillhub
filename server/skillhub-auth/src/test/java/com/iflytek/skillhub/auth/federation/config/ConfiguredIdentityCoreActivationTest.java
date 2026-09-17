package com.iflytek.skillhub.auth.federation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguredIdentityCoreActivationTest {

    @Test
    void defaultsToLegacyForPlatformAndEveryOrganization() {
        ConfiguredIdentityCoreActivation activation = activation(
                new IdentityCoreProperties(),
                new EnterpriseIdentityRolloutProperties()
        );

        assertThat(activation.modeFor(Optional.empty())).isEqualTo(IdentityCoreMode.LEGACY);
        assertThat(activation.modeFor(Optional.of("org_1"))).isEqualTo(IdentityCoreMode.LEGACY);
    }

    @Test
    void organizationMustBeExplicitlyAllowlistedEvenWhenCoreModeIsActive() {
        IdentityCoreProperties core = new IdentityCoreProperties();
        core.setMode(IdentityCoreMode.ACTIVE);
        EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
        rollout.setOrganizationAllowlist(Set.of("org_allowed"));
        ConfiguredIdentityCoreActivation activation = activation(core, rollout);

        assertThat(activation.modeFor(Optional.empty())).isEqualTo(IdentityCoreMode.ACTIVE);
        assertThat(activation.modeFor(Optional.of("org_allowed"))).isEqualTo(IdentityCoreMode.ACTIVE);
        assertThat(activation.modeFor(Optional.of("org_other"))).isEqualTo(IdentityCoreMode.LEGACY);
    }

    @Test
    void shadowModeIsAlsoLimitedToAllowlistedOrganizations() {
        IdentityCoreProperties core = new IdentityCoreProperties();
        core.setMode(IdentityCoreMode.SHADOW);
        EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
        rollout.setOrganizationAllowlist(Set.of(" org_shadow "));
        ConfiguredIdentityCoreActivation activation = activation(core, rollout);

        assertThat(activation.modeFor(Optional.of("org_shadow"))).isEqualTo(IdentityCoreMode.SHADOW);
        assertThat(activation.modeFor(Optional.of("org_other"))).isEqualTo(IdentityCoreMode.LEGACY);
    }

    @Test
    void bindsDocumentedPropertyNamesAndPublishesActivationPort() {
        new ApplicationContextRunner()
                .withUserConfiguration(EnterpriseIdentityCoreConfiguration.class)
                .withBean(ExternalIdentityRepository.class,
                        () -> mock(ExternalIdentityRepository.class))
                .withBean(IdentityBindingRepository.class,
                        () -> mock(IdentityBindingRepository.class))
                .withPropertyValues(
                        "skillhub.identity.core.mode=SHADOW",
                        "skillhub.enterprise.organization-allowlist=org_1,org_2"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation.class
                    );
                    var activation = context.getBean(
                            com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation.class
                    );
                    assertThat(activation.modeFor(Optional.of("org_1"))).isEqualTo(IdentityCoreMode.SHADOW);
                    assertThat(activation.modeFor(Optional.of("org_3"))).isEqualTo(IdentityCoreMode.LEGACY);
                });
    }

    private static ConfiguredIdentityCoreActivation activation(
            IdentityCoreProperties core,
            EnterpriseIdentityRolloutProperties rollout
    ) {
        return new ConfiguredIdentityCoreActivation(core, rollout);
    }
}
