package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileAuthorityResolverTest {

    private static final AttributeKey DISPLAY_NAME = new AttributeKey("profile.display_name");
    private static final AttributeKey DEPARTMENT = new AttributeKey("profile.department");

    private final ProfileAuthorityResolver resolver = new ProfileAuthorityResolver();

    @Test
    void lowerAuthorityLoginCannotOverwriteUserManagedGlobalField() {
        ProfileFieldCoordinate field = field(ProfileScope.global(), DISPLAY_NAME);
        ProfileValueSource user = source(ProfileSourceKind.USER_MANAGED, "usr_1", field.scope());
        ProfileValueSource login = source(ProfileSourceKind.LOGIN_ASSERTION, "login_1", field.scope());
        ProfileFieldAuthorityPolicy policy = policy(field,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10),
                ProfileAuthorityRule.forKind(ProfileSourceKind.USER_MANAGED, 20));
        ProfileFieldState current = state(field, "Alice", user, 20);

        ProfileFieldResolution result = resolver.resolve(
                Optional.of(current),
                candidate(field, "Alice from login", login),
                policy
        );

        assertThat(result).isEqualTo(new ProfileFieldResolution.Preserved(
                current,
                ProfileFieldResolutionReason.LOWER_AUTHORITY
        ));
    }

    @Test
    void lowerAuthorityLoginCannotOverwriteOrganizationAdminManagedField() {
        ProfileFieldCoordinate field = field(ProfileScope.organization("org_1"), DISPLAY_NAME);
        ProfileValueSource organizationAdmin = source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", field.scope());
        ProfileValueSource login = source(ProfileSourceKind.LOGIN_ASSERTION, "login_1", field.scope());
        ProfileFieldAuthorityPolicy policy = policy(field,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10),
                ProfileAuthorityRule.forSource(ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", 30));
        ProfileFieldState current = state(field, "Managed Alice", organizationAdmin, 30);

        ProfileFieldResolution result = resolver.resolve(
                Optional.of(current),
                candidate(field, "Login Alice", login),
                policy
        );

        assertThat(result).isEqualTo(new ProfileFieldResolution.Preserved(
                current,
                ProfileFieldResolutionReason.LOWER_AUTHORITY
        ));
    }

    @Test
    void authorizedSourceCanFillEmptyFieldAndRefreshItsOwnValue() {
        ProfileFieldCoordinate field = field(ProfileScope.organization("org_1"), DISPLAY_NAME);
        ProfileValueSource login = source(ProfileSourceKind.LOGIN_ASSERTION, "login_1", field.scope());
        ProfileFieldAuthorityPolicy policy = policy(field,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10));

        ProfileFieldResolution first = resolver.resolve(
                Optional.empty(), candidate(field, "Alice", login), policy);
        ProfileFieldState created = ((ProfileFieldResolution.Applied) first).state();
        ProfileFieldResolution refreshed = resolver.resolve(
                Optional.of(created), candidate(field, "Alice Updated", login), policy);

        assertThat(created.value()).isEqualTo(new AttributeValue.Text("Alice"));
        assertThat(created.authority()).isEqualTo(new ProfileFieldAuthority(login, 10));
        assertThat(refreshed).isEqualTo(new ProfileFieldResolution.Applied(
                state(field, "Alice Updated", login, 10)
        ));
    }

    @Test
    void higherAuthorityOrganizationAdminCanTakeOwnershipFromLogin() {
        ProfileFieldCoordinate field = field(ProfileScope.organization("org_1"), DEPARTMENT);
        ProfileValueSource login = source(ProfileSourceKind.LOGIN_ASSERTION, "login_1", field.scope());
        ProfileValueSource organizationAdmin = source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", field.scope());
        ProfileFieldAuthorityPolicy policy = policy(field,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10),
                ProfileAuthorityRule.forSource(ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", 30));

        ProfileFieldResolution result = resolver.resolve(
                Optional.of(state(field, "Engineering", login, 10)),
                candidate(field, "Research", organizationAdmin),
                policy
        );

        assertThat(result).isEqualTo(new ProfileFieldResolution.Applied(
                state(field, "Research", organizationAdmin, 30)
        ));
    }

    @Test
    void authorityIsConfiguredPerFieldAndPerSource() {
        ProfileScope scope = ProfileScope.organization("org_1");
        ProfileValueSource organizationAdmin = source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", scope);
        ProfileFieldCoordinate displayName = field(scope, DISPLAY_NAME);
        ProfileFieldCoordinate department = field(scope, DEPARTMENT);
        ProfileFieldAuthorityPolicy displayNamePolicy = policy(displayName,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10));
        ProfileFieldAuthorityPolicy departmentPolicy = policy(department,
                ProfileAuthorityRule.forSource(ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", 30));

        ProfileFieldResolution denied = resolver.resolve(
                Optional.empty(), candidate(displayName, "Managed Alice", organizationAdmin), displayNamePolicy);
        ProfileFieldResolution applied = resolver.resolve(
                Optional.empty(), candidate(department, "Research", organizationAdmin), departmentPolicy);

        assertThat(denied).isEqualTo(new ProfileFieldResolution.Rejected(
                ProfileFieldResolutionReason.SOURCE_NOT_AUTHORIZED
        ));
        assertThat(applied).isInstanceOf(ProfileFieldResolution.Applied.class);
    }

    @Test
    void equalAuthorityFromDifferentSourcesDoesNotDependOnArrivalOrder() {
        ProfileFieldCoordinate field = field(ProfileScope.organization("org_1"), DEPARTMENT);
        ProfileValueSource adminOne = source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", field.scope());
        ProfileValueSource adminTwo = source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_2", field.scope());
        ProfileFieldAuthorityPolicy policy = policy(field,
                ProfileAuthorityRule.forSource(ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", 30),
                ProfileAuthorityRule.forSource(ProfileSourceKind.ORGANIZATION_ADMIN, "admin_2", 30));
        ProfileFieldState current = state(field, "Research", adminOne, 30);

        ProfileFieldResolution result = resolver.resolve(
                Optional.of(current), candidate(field, "Engineering", adminTwo), policy);

        assertThat(result).isEqualTo(new ProfileFieldResolution.Preserved(
                current,
                ProfileFieldResolutionReason.EQUAL_AUTHORITY_CONFLICT
        ));
    }

    @Test
    void sourceAndFieldScopesCannotCrossOrganizationsOrGlobalBoundary() {
        ProfileScope organizationOne = ProfileScope.organization("org_1");
        ProfileScope organizationTwo = ProfileScope.organization("org_2");
        ProfileValueSource orgOneLogin = source(
                ProfileSourceKind.LOGIN_ASSERTION, "login_1", organizationOne);
        ProfileFieldCoordinate orgTwoField = field(organizationTwo, DISPLAY_NAME);
        ProfileFieldAuthorityPolicy policy = policy(orgTwoField,
                ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10));

        assertThatThrownBy(() -> candidate(orgTwoField, "Alice", orgOneLogin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThatThrownBy(() -> source(
                ProfileSourceKind.ORGANIZATION_ADMIN, "admin_1", ProfileScope.global()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organization");
    }

    @Test
    void policyRejectsDuplicateRulesAndGlobalOrganizationAdminAuthority() {
        ProfileFieldCoordinate globalField = field(ProfileScope.global(), DISPLAY_NAME);

        assertThatThrownBy(() -> policy(globalField,
                ProfileAuthorityRule.forKind(ProfileSourceKind.ORGANIZATION_ADMIN, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("global");
        assertThatThrownBy(() -> new ProfileFieldAuthorityPolicy(
                globalField,
                List.of(
                        ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 10),
                        ProfileAuthorityRule.forKind(ProfileSourceKind.LOGIN_ASSERTION, 20)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    private static ProfileFieldAuthorityPolicy policy(
            ProfileFieldCoordinate field,
            ProfileAuthorityRule... rules
    ) {
        return new ProfileFieldAuthorityPolicy(field, List.of(rules));
    }

    private static ProfileFieldCoordinate field(ProfileScope scope, AttributeKey key) {
        return new ProfileFieldCoordinate(scope, key);
    }

    private static ProfileValueSource source(
            ProfileSourceKind kind,
            String sourceId,
            ProfileScope scope
    ) {
        return new ProfileValueSource(kind, sourceId, scope);
    }

    private static ProfileFieldCandidate candidate(
            ProfileFieldCoordinate field,
            String value,
            ProfileValueSource source
    ) {
        return new ProfileFieldCandidate(field, new AttributeValue.Text(value), source);
    }

    private static ProfileFieldState state(
            ProfileFieldCoordinate field,
            String value,
            ProfileValueSource source,
            int priority
    ) {
        return new ProfileFieldState(
                field,
                new AttributeValue.Text(value),
                new ProfileFieldAuthority(source, priority)
        );
    }
}
