package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.federation.core.Assurance;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterException;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterFailureReason;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.federation.core.VerifiedAuthenticationFactsAdapter;
import com.iflytek.skillhub.auth.federation.core.VerifiedEmail;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Adapts the existing normalized OAuth facts to the protocol-neutral assertion Interface. */
@Component
public final class LegacyOAuthVerifiedFactsAdapter
        implements VerifiedAuthenticationFactsAdapter<OAuthClaims> {

    private static final Assurance VERIFIED_EMAIL = new Assurance("email_verified");

    private final Clock clock;

    public LegacyOAuthVerifiedFactsAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IdentityAssertion toAssertion(OAuthClaims claims) {
        try {
            Objects.requireNonNull(claims, "claims");
            String provider = LegacyOAuthIdentityCoordinate.requireProvider(claims.provider());
            Optional<VerifiedEmail> email = verifiedEmail(claims);
            return new IdentityAssertion(
                    Optional.empty(),
                    LegacyOAuthIdentityCoordinate.connectionId(provider),
                    LegacyOAuthIdentityCoordinate.ISSUER,
                    new SubjectRef(LegacyOAuthIdentityCoordinate.SUBJECT_TYPE, claims.subject()),
                    email,
                    optionalText(claims.providerLogin()),
                    optionalText(claims.providerLogin()),
                    optionalMapText(claims.extra(), "avatar_url"),
                    email.isPresent() ? Set.of(VERIFIED_EMAIL) : Set.of(),
                    Instant.now(clock),
                    Map.of()
            );
        } catch (IllegalArgumentException | NullPointerException invalidFacts) {
            throw new AuthenticationAdapterException(
                    AuthenticationAdapterFailureReason.INVALID_ASSERTION
            );
        }
    }

    private static Optional<VerifiedEmail> verifiedEmail(OAuthClaims claims) {
        if (!claims.emailVerified()) {
            return Optional.empty();
        }
        return optionalText(claims.email()).map(VerifiedEmail::new);
    }

    private static Optional<String> optionalMapText(Map<String, Object> values, String key) {
        if (values == null) {
            return Optional.empty();
        }
        Object value = values.get(key);
        return value instanceof String text ? optionalText(text) : Optional.empty();
    }

    private static Optional<String> optionalText(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

}
