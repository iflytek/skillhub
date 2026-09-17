package com.iflytek.skillhub.auth.federation.conformance;

import com.iflytek.skillhub.auth.federation.core.Assurance;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterException;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterFailureReason;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.VerifiedAuthenticationFactsAdapter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Executable Interface shared by every adapter that emits verified identity facts. */
public abstract class AuthenticationAdapterConformance<F> {

    @Test
    void verifiedFactsProduceStableProtocolNeutralIdentity() {
        IdentityAssertion first = adapter().toAssertion(validFacts());
        IdentityAssertion second = adapter().toAssertion(validFacts());
        ExpectedIdentity expected = expectedIdentity();

        assertThat(first.connectionId()).isEqualTo(second.connectionId());
        assertThat(first.issuer()).isEqualTo(second.issuer());
        assertThat(first.subject()).isEqualTo(second.subject());
        assertThat(first.connectionId()).isEqualTo(expected.connectionId());
        assertThat(first.issuer()).isEqualTo(expected.issuer());
        assertThat(first.subject().type().value()).isEqualTo(expected.subjectType());
        assertThat(first.subject().value()).isEqualTo(expected.subject());
        assertThat(first.email()).get().extracting("value").isEqualTo(expected.verifiedEmail());
        assertThat(first.assurance()).contains(new Assurance(expected.emailAssurance()));
    }

    @Test
    void unverifiedEmailIsNotPromotedToAVerifiedFact() {
        IdentityAssertion assertion = adapter().toAssertion(factsWithoutVerifiedEmail());

        assertThat(assertion.email()).isEmpty();
        assertThat(assertion.assurance())
                .noneMatch(assurance -> assurance.value().equals("email_verified"));
    }

    @Test
    void rawSecretsNeverCrossTheAssertionInterface() {
        String secret = "secret-material-that-must-not-cross";

        IdentityAssertion assertion = adapter().toAssertion(factsContainingSecret(secret));

        assertThat(assertion.toString()).doesNotContain(secret);
    }

    @Test
    void invalidFactsProduceAStandardRedactedError() {
        String secret = "invalid-secret-that-must-not-leak";

        assertThatThrownBy(() -> adapter().toAssertion(invalidFactsContainingSecret(secret)))
                .isInstanceOfSatisfying(AuthenticationAdapterException.class, failure -> {
                    assertThat(failure.reason())
                            .isEqualTo(AuthenticationAdapterFailureReason.INVALID_ASSERTION);
                    assertThat(failure.getMessage())
                            .isEqualTo(AuthenticationAdapterFailureReason.INVALID_ASSERTION.publicMessage())
                            .doesNotContain(secret);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.toString()).doesNotContain(secret);
                });
    }

    @Test
    void adapterInterfaceAndImplementationExposeNoPlatformStateSideEffects() {
        Set<String> forbiddenTypeFragments = Set.of(
                "UserAccount",
                "OrganizationMembership",
                "NamespaceMember",
                "IdentityBindingRepository",
                "PlatformPrincipal",
                "SessionRepository"
        );
        Method[] interfaceMethods = VerifiedAuthenticationFactsAdapter.class.getDeclaredMethods();

        assertThat(interfaceMethods).hasSize(1);
        assertThat(interfaceMethods[0].getReturnType()).isEqualTo(IdentityAssertion.class);

        Stream<Class<?>> exposedInterfaceTypes = Arrays.stream(interfaceMethods)
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())
                ));
        Stream<Class<?>> implementationDependencyTypes = Stream.concat(
                Arrays.stream(adapter().getClass().getDeclaredFields()).map(field -> field.getType()),
                Arrays.stream(adapter().getClass().getDeclaredConstructors())
                        .flatMap((Constructor<?> constructor) -> Arrays.stream(constructor.getParameterTypes()))
        );

        assertThat(Stream.concat(exposedInterfaceTypes, implementationDependencyTypes)
                .map(Class::getName))
                .noneMatch(name -> forbiddenTypeFragments.stream().anyMatch(name::contains));
    }

    protected abstract VerifiedAuthenticationFactsAdapter<F> adapter();

    protected abstract F validFacts();

    protected abstract F factsWithoutVerifiedEmail();

    protected abstract F factsContainingSecret(String secret);

    protected abstract F invalidFactsContainingSecret(String secret);

    protected abstract ExpectedIdentity expectedIdentity();

    public record ExpectedIdentity(
            String connectionId,
            URI issuer,
            String subjectType,
            String subject,
            String verifiedEmail,
            String emailAssurance
    ) {
    }
}
