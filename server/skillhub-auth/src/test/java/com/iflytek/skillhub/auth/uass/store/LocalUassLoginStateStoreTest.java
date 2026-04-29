package com.iflytek.skillhub.auth.uass.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LocalUassLoginStateStoreTest {

    @Test
    void find_returnsStoredStateBeforeExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T15:00:00Z"));
        LocalUassLoginStateStore store = new LocalUassLoginStateStore(Duration.ofMinutes(5), clock);
        UassLoginState loginState = loginState(clock.instant(), "/dashboard");

        store.save("state-1", loginState);

        assertThat(store.find("state-1")).contains(loginState);
    }

    @Test
    void find_returnsEmptyAfterTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T15:00:00Z"));
        LocalUassLoginStateStore store = new LocalUassLoginStateStore(Duration.ofMinutes(5), clock);
        store.save("state-1", loginState(clock.instant(), "/dashboard"));

        clock.advance(Duration.ofMinutes(6));

        assertThat(store.find("state-1")).isEmpty();
    }

    @Test
    void consume_returnsStateOnceAndDeletesIt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T15:00:00Z"));
        LocalUassLoginStateStore store = new LocalUassLoginStateStore(Duration.ofMinutes(5), clock);
        UassLoginState loginState = loginState(clock.instant(), "/console");
        store.save("state-1", loginState);

        assertThat(store.consume("state-1")).contains(loginState);
        assertThat(store.find("state-1")).isEmpty();
    }

    @Test
    void save_cleansUpExpiredStatesBeforeAddingNewEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T15:00:00Z"));
        LocalUassLoginStateStore store = new LocalUassLoginStateStore(Duration.ofMinutes(5), clock);
        store.save("state-1", loginState(clock.instant(), "/stale"));

        clock.advance(Duration.ofMinutes(6));
        store.save("state-2", loginState(clock.instant(), "/fresh"));

        assertThat(store.stateCount()).isEqualTo(1);
        assertThat(store.find("state-1")).isEmpty();
        assertThat(store.find("state-2")).isPresent();
    }

    private static UassLoginState loginState(Instant createdAt, String returnTo) {
        return new UassLoginState(returnTo, createdAt, "uass", "fingerprint-1");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
