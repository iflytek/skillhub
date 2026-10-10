package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityAssertionTest {

    @Test
    void createsImmutableProtocolNeutralAssertion() {
        Set<Assurance> assurance = new HashSet<>(Set.of(new Assurance("multi-factor")));
        AttributeKey department = new AttributeKey("profile.department");
        Map<AttributeKey, NormalizedAttribute> attributes = new HashMap<>(Map.of(
                department,
                new NormalizedAttribute(
                        department,
                        new AttributeValue.Text("Research"),
                        Set.of(new Assurance("directory-verified"))
                )
        ));

        IdentityAssertion assertion = new IdentityAssertion(
                Optional.of("org_1"),
                "connection_1",
                URI.create("https://identity.example.com"),
                new SubjectRef(new SubjectType("opaque-user-id"), "subject-123"),
                Optional.of(new VerifiedEmail("Alice@Example.com")),
                Optional.of("alice"),
                Optional.of("Alice"),
                Optional.empty(),
                assurance,
                Instant.parse("2026-09-07T12:00:00Z"),
                attributes
        );

        assurance.clear();
        attributes.clear();

        assertThat(assertion.organizationId()).contains("org_1");
        assertThat(assertion.email()).map(VerifiedEmail::value).contains("alice@example.com");
        assertThat(assertion.assurance()).containsExactly(new Assurance("multi-factor"));
        assertThat(assertion.attributes()).containsOnlyKeys(department);
        assertThatThrownBy(() -> assertion.assurance().add(new Assurance("single-factor")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> assertion.attributes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingConnection() {
        assertThatThrownBy(() -> validAssertion(" ", URI.create("https://identity.example.com"), subject()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionId");
    }

    @Test
    void rejectsMissingOrRelativeIssuer() {
        assertThatThrownBy(() -> validAssertion("connection_1", null, subject()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> validAssertion("connection_1", URI.create("/tenant"), subject()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void rejectsMissingOrBlankSubject() {
        assertThatThrownBy(() -> validAssertion(
                "connection_1", URI.create("https://identity.example.com"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() -> new SubjectRef(new SubjectType("opaque-user-id"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject value");
        assertThatThrownBy(() -> new SubjectType(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject type");
    }

    @Test
    void rejectsMismatchedAttributeMapKey() {
        AttributeKey mapKey = new AttributeKey("profile.department");
        AttributeKey attributeKey = new AttributeKey("profile.cost-center");

        assertThatThrownBy(() -> new IdentityAssertion(
                Optional.empty(),
                "connection_1",
                URI.create("urn:skillhub:legacy"),
                subject(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                Instant.parse("2026-09-07T12:00:00Z"),
                Map.of(mapKey, new NormalizedAttribute(
                        attributeKey,
                        new AttributeValue.Text("cc-1"),
                        Set.of()
                ))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute key");
    }

    @Test
    void publicContractHasNoRawCredentialOrTokenComponents() {
        assertThat(IdentityAssertion.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain(
                        "accessToken", "idToken", "refreshToken", "rawToken",
                        "rawClaims", "rawAssertion", "credential", "password"
                );
    }

    private static IdentityAssertion validAssertion(
            String connectionId,
            URI issuer,
            SubjectRef subject
    ) {
        return new IdentityAssertion(
                Optional.empty(),
                connectionId,
                issuer,
                subject,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                Instant.parse("2026-09-07T12:00:00Z"),
                Map.of()
        );
    }

    private static SubjectRef subject() {
        return new SubjectRef(new SubjectType("opaque-user-id"), "subject-123");
    }
}
