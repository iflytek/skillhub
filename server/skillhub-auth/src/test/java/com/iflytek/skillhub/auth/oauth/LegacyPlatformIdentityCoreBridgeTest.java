package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.federation.adapter.LegacyOAuthVerifiedFactsAdapter;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.IdentityLoginResolution;
import com.iflytek.skillhub.auth.federation.migration.IdentityBindingReadMetrics;
import com.iflytek.skillhub.auth.federation.migration.LegacyIdentityBindingDualReader;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LegacyPlatformIdentityCoreBridgeTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-07T14:00:00Z"), ZoneOffset.UTC);

    @Test
    void legacyModeDoesNotEnterUnifiedCoreOrReadBindings() {
        IdentityBindingRepository bindings = mock(IdentityBindingRepository.class);
        LegacyPlatformIdentityCoreBridge bridge = bridge(IdentityCoreMode.LEGACY, bindings);

        LegacyPlatformIdentityDecision decision = bridge.evaluate(claims());

        assertThat(decision.mode()).isEqualTo(IdentityCoreMode.LEGACY);
        assertThat(decision.resolution()).isEmpty();
        verify(bindings, never()).findByProviderCodeAndSubject("github", "subject-1");
    }

    @Test
    void shadowModeEvaluatesExactLegacyBindingWithoutChangingLoginOutcome() {
        IdentityBindingRepository bindings = mock(IdentityBindingRepository.class);
        when(bindings.findByProviderCodeAndSubject("github", "subject-1"))
                .thenReturn(Optional.of(new IdentityBinding(
                        "usr_1", "github", "subject-1", "alice")));
        LegacyPlatformIdentityCoreBridge bridge = bridge(IdentityCoreMode.SHADOW, bindings);

        LegacyPlatformIdentityDecision decision = bridge.evaluate(claims());

        assertThat(decision.mode()).isEqualTo(IdentityCoreMode.SHADOW);
        assertThat(decision.resolution()).hasValueSatisfying(resolution ->
                assertThat(resolution).isInstanceOf(IdentityLoginResolution.Matched.class));
        verify(bindings).findByProviderCodeAndSubject("github", "subject-1");
    }

    @Test
    void activeModeEvaluatesNewAndExistingLegacyIdentities() {
        IdentityBindingRepository bindings = mock(IdentityBindingRepository.class);
        LegacyPlatformIdentityCoreBridge bridge = bridge(IdentityCoreMode.ACTIVE, bindings);

        assertThat(bridge.evaluate(claims()).resolution())
                .hasValueSatisfying(resolution ->
                        assertThat(resolution).isInstanceOf(IdentityLoginResolution.ProvisionNew.class));
        when(bindings.findByProviderCodeAndSubject("github", "subject-1"))
                .thenReturn(Optional.of(new IdentityBinding(
                        "usr_1", "github", "subject-1", "alice")));
        assertThat(bridge.evaluate(claims()).resolution())
                .hasValueSatisfying(resolution ->
                        assertThat(resolution).isInstanceOf(IdentityLoginResolution.Matched.class));
    }

    @Test
    void shadowFailureCannotBreakLegacyLoginButActiveFailureIsFailClosed() {
        IdentityBindingRepository bindings = mock(IdentityBindingRepository.class);
        when(bindings.findByProviderCodeAndSubject("github", "subject-1"))
                .thenThrow(new IllegalStateException("provider-private-error subject-1 alice@example.com"));

        Logger logger = (Logger) LoggerFactory.getLogger(LegacyPlatformIdentityCoreBridge.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(Level.WARN);
        appender.start();
        logger.addAppender(appender);
        try {
            LegacyPlatformIdentityDecision shadowDecision =
                    bridge(IdentityCoreMode.SHADOW, bindings).evaluate(claims());
            assertThat(shadowDecision.mode()).isEqualTo(IdentityCoreMode.SHADOW);
            assertThat(shadowDecision.resolution()).isEmpty();
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message).doesNotContain(
                            "provider-private-error", "subject-1", "alice@example.com"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
        assertThatThrownBy(() -> bridge(IdentityCoreMode.ACTIVE, bindings).evaluate(claims()))
                .isInstanceOf(OAuthIdentityCoreException.class)
                .hasMessageContaining("Unified identity evaluation failed");
    }

    private static LegacyPlatformIdentityCoreBridge bridge(
            IdentityCoreMode mode,
            IdentityBindingRepository bindings
    ) {
        return new LegacyPlatformIdentityCoreBridge(
                ignored -> mode,
                new LegacyIdentityBindingDualReader(
                        mock(ExternalIdentityRepository.class),
                        bindings,
                        IdentityBindingReadMetrics.noop()
                ),
                new LegacyOAuthVerifiedFactsAdapter(CLOCK)
        );
    }

    private static OAuthClaims claims() {
        return new OAuthClaims(
                "github", "subject-1", "alice@example.com", true, "alice", Map.of()
        );
    }
}
