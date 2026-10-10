package com.iflytek.skillhub.auth.federation.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.federation.adapter.LegacyOAuthIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentity;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LegacyIdentityBindingDualReaderTest {

    private static final String PROVIDER = "github";
    private static final String SUBJECT = "subject-1";
    private static final ExternalIdentityCoordinate COORDINATE = new ExternalIdentityCoordinate(
            Optional.empty(),
            LegacyOAuthIdentityCoordinate.connectionId(PROVIDER),
            LegacyOAuthIdentityCoordinate.ISSUER,
            new SubjectRef(LegacyOAuthIdentityCoordinate.SUBJECT_TYPE, SUBJECT)
    );

    private ExternalIdentityRepository v2;
    private IdentityBindingRepository legacy;
    private IdentityBindingReadMetrics metrics;
    private LegacyIdentityBindingDualReader reader;

    @BeforeEach
    void setUp() {
        v2 = mock(ExternalIdentityRepository.class);
        legacy = mock(IdentityBindingRepository.class);
        metrics = mock(IdentityBindingReadMetrics.class);
        reader = new LegacyIdentityBindingDualReader(v2, legacy, metrics);
    }

    @Test
    void legacyModeBypassesV2AndRemainsAnImmediateRollbackPath() {
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(binding("user-legacy")));

        var targets = reader.findExactBinding(
                IdentityCoreMode.LEGACY, COORDINATE, PROVIDER, SUBJECT);

        assertThat(targets).singleElement()
                .extracting(target -> target.userId().orElseThrow())
                .isEqualTo("user-legacy");
        verify(v2, never()).findByCoordinate(COORDINATE);
        verify(metrics).record(IdentityCoreMode.LEGACY, IdentityBindingReadOutcome.LEGACY_HIT);
    }

    @Test
    void shadowReadsV2FirstButKeepsLegacyAuthoritativeOnMismatch() {
        when(v2.findByCoordinate(COORDINATE)).thenReturn(Optional.of(v2("user-v2")));
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(binding("user-legacy")));

        var targets = reader.findExactBinding(
                IdentityCoreMode.SHADOW, COORDINATE, PROVIDER, SUBJECT);

        assertThat(targets).singleElement()
                .extracting(target -> target.userId().orElseThrow())
                .isEqualTo("user-legacy");
        InOrder order = inOrder(v2, legacy);
        order.verify(v2).findByCoordinate(COORDINATE);
        order.verify(legacy).findByProviderCodeAndSubject(PROVIDER, SUBJECT);
        verify(metrics).record(IdentityCoreMode.SHADOW, IdentityBindingReadOutcome.MISMATCH);
    }

    @Test
    void activeUsesV2WhenBothStoresMatch() {
        when(v2.findByCoordinate(COORDINATE)).thenReturn(Optional.of(v2("user-1")));
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(binding("user-1")));

        var targets = reader.findExactBinding(
                IdentityCoreMode.ACTIVE, COORDINATE, PROVIDER, SUBJECT);

        assertThat(targets).singleElement()
                .extracting(target -> target.userId().orElseThrow())
                .isEqualTo("user-1");
        verify(metrics).record(IdentityCoreMode.ACTIVE, IdentityBindingReadOutcome.MATCH);
    }

    @Test
    void activeFallsBackToLegacyWhenV2IsMissing() {
        when(v2.findByCoordinate(COORDINATE)).thenReturn(Optional.empty());
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(binding("user-legacy")));

        var targets = reader.findExactBinding(
                IdentityCoreMode.ACTIVE, COORDINATE, PROVIDER, SUBJECT);

        assertThat(targets).singleElement()
                .extracting(target -> target.userId().orElseThrow())
                .isEqualTo("user-legacy");
        verify(metrics).record(
                IdentityCoreMode.ACTIVE,
                IdentityBindingReadOutcome.V2_MISS_LEGACY_FALLBACK
        );
    }

    @Test
    void activeFailsClosedWhenStoresPointToDifferentAccounts() {
        when(v2.findByCoordinate(COORDINATE)).thenReturn(Optional.of(v2("user-v2")));
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(binding("user-legacy")));

        assertThatThrownBy(() -> reader.findExactBinding(
                IdentityCoreMode.ACTIVE, COORDINATE, PROVIDER, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resolve different accounts");
        verify(metrics).record(IdentityCoreMode.ACTIVE, IdentityBindingReadOutcome.MISMATCH);
    }

    @Test
    void activeFailsClosedForV2OnlyPlatformIdentityWhileLegacyWritesRemainEnabled() {
        when(v2.findByCoordinate(COORDINATE)).thenReturn(Optional.of(v2("user-v2")));
        when(legacy.findByProviderCodeAndSubject(PROVIDER, SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.findExactBinding(
                IdentityCoreMode.ACTIVE, COORDINATE, PROVIDER, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only in V2");
        verify(metrics).record(IdentityCoreMode.ACTIVE, IdentityBindingReadOutcome.V2_ONLY);
    }

    private static IdentityBinding binding(String userId) {
        return new IdentityBinding(userId, PROVIDER, SUBJECT, "alice");
    }

    private static ExternalIdentity v2(String userId) {
        return ExternalIdentity.bind(COORDINATE, userId, Instant.parse("2026-09-07T12:00:00Z"));
    }
}
