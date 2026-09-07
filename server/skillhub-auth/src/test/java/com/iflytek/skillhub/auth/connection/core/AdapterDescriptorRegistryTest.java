package com.iflytek.skillhub.auth.connection.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdapterDescriptorRegistryTest {

    @Test
    void require_resolvesVersionedBuiltInLoginAndDirectoryDescriptors() {
        AdapterDescriptor login = loginDescriptor(
                "oidc-standard",
                new AdapterContractVersion(1, 2),
                InteractionModel.REDIRECT,
                Set.of(
                        AdapterCapability.IDENTITY_ASSERTION,
                        AdapterCapability.VERIFIED_EMAIL_ASSERTION
                )
        );
        AdapterDescriptor directory = new AdapterDescriptor(
                new AdapterKey("scim-directory"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.DIRECTORY,
                1,
                Optional.empty(),
                Set.of(
                        AdapterCapability.DIRECTORY_USERS,
                        AdapterCapability.DIRECTORY_GROUPS,
                        AdapterCapability.DEPROVISIONING
                )
        );
        AdapterDescriptorRegistry registry = new AdapterDescriptorRegistry(List.of(login, directory));

        assertThat(registry.require(new AdapterKey("oidc-standard"), 1)).isSameAs(login);
        assertThat(registry.require(new AdapterKey("scim-directory"), 1)).isSameAs(directory);
        assertThat(registry.descriptors()).containsExactly(login, directory);
    }

    @Test
    void constructor_rejectsDuplicateAdapterKeyAndMajorVersion() {
        AdapterDescriptor first = loginDescriptor(
                "duplicate-login",
                new AdapterContractVersion(1, 0),
                InteractionModel.REDIRECT,
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );
        AdapterDescriptor second = loginDescriptor(
                "duplicate-login",
                new AdapterContractVersion(1, 4),
                InteractionModel.REDIRECT,
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(second, first)),
                AdapterRegistryFailureReason.DUPLICATE_REGISTRATION
        );
    }

    @Test
    void constructor_rejectsUnsupportedContractMajorVersion() {
        AdapterDescriptor future = loginDescriptor(
                "future-login",
                new AdapterContractVersion(2, 0),
                InteractionModel.REDIRECT,
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(future)),
                AdapterRegistryFailureReason.UNSUPPORTED_CONTRACT_VERSION
        );
    }

    @Test
    void constructor_rejectsLoginWithoutInteractionModel() {
        AdapterDescriptor invalid = new AdapterDescriptor(
                new AdapterKey("missing-interaction"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.LOGIN,
                1,
                Optional.empty(),
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(invalid)),
                AdapterRegistryFailureReason.INVALID_INTERACTION_MODEL
        );
    }

    @Test
    void constructor_rejectsDirectoryWithLoginInteractionModel() {
        AdapterDescriptor invalid = new AdapterDescriptor(
                new AdapterKey("directory-redirect"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.DIRECTORY,
                1,
                Optional.of(InteractionModel.REDIRECT),
                Set.of(AdapterCapability.DIRECTORY_USERS)
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(invalid)),
                AdapterRegistryFailureReason.INVALID_INTERACTION_MODEL
        );
    }

    @Test
    void constructor_rejectsCapabilitiesFromAnotherConnectionKind() {
        AdapterDescriptor invalid = loginDescriptor(
                "login-with-directory-write",
                new AdapterContractVersion(1, 0),
                InteractionModel.PASSIVE_ASSERTION,
                Set.of(
                        AdapterCapability.IDENTITY_ASSERTION,
                        AdapterCapability.DIRECTORY_USERS
                )
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(invalid)),
                AdapterRegistryFailureReason.INVALID_CAPABILITY_SET
        );
    }

    @Test
    void constructor_rejectsCapabilitiesWhoseDependenciesAreMissing() {
        AdapterDescriptor invalid = new AdapterDescriptor(
                new AdapterKey("groups-without-users"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.DIRECTORY,
                1,
                Optional.empty(),
                Set.of(AdapterCapability.DIRECTORY_GROUPS)
        );

        assertRegistryFailure(
                () -> new AdapterDescriptorRegistry(List.of(invalid)),
                AdapterRegistryFailureReason.MISSING_REQUIRED_CAPABILITY
        );
    }

    @Test
    void require_failsClosedForUnknownStableKeyWithoutLoadingAClassName() {
        AdapterDescriptorRegistry registry = new AdapterDescriptorRegistry(List.of());

        assertRegistryFailure(
                () -> registry.require(new AdapterKey("database-class-name"), 1),
                AdapterRegistryFailureReason.ADAPTER_NOT_REGISTERED
        );
    }

    private static AdapterDescriptor loginDescriptor(
            String key,
            AdapterContractVersion contractVersion,
            InteractionModel interactionModel,
            Set<AdapterCapability> capabilities
    ) {
        return new AdapterDescriptor(
                new AdapterKey(key),
                contractVersion,
                ConnectionKind.LOGIN,
                1,
                Optional.of(interactionModel),
                capabilities
        );
    }

    private static void assertRegistryFailure(
            Runnable operation,
            AdapterRegistryFailureReason reason
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        AdapterRegistryException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(reason)
                );
    }
}
