package com.iflytek.skillhub.auth.uass.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.StringUtils;

public class LocalUassLoginStateStore implements UassLoginStateStore {

    private final Duration ttl;
    private final Clock clock;
    private final Map<String, StoredState> states = new ConcurrentHashMap<>();

    public LocalUassLoginStateStore(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    LocalUassLoginStateStore(Duration ttl, Clock clock) {
        this.ttl = requirePositive(ttl);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void save(String state, UassLoginState loginState) {
        cleanupExpired();
        states.put(normalizeState(state), new StoredState(
                Objects.requireNonNull(loginState, "loginState must not be null"),
                now().plus(ttl)
        ));
    }

    @Override
    public Optional<UassLoginState> find(String state) {
        String normalizedState = normalizeState(state);
        StoredState storedState = states.get(normalizedState);
        if (storedState == null) {
            return Optional.empty();
        }
        if (isExpired(storedState)) {
            states.remove(normalizedState, storedState);
            return Optional.empty();
        }
        return Optional.of(storedState.loginState());
    }

    @Override
    public Optional<UassLoginState> consume(String state) {
        String normalizedState = normalizeState(state);
        StoredState storedState = states.remove(normalizedState);
        if (storedState == null || isExpired(storedState)) {
            return Optional.empty();
        }
        return Optional.of(storedState.loginState());
    }

    @Override
    public void delete(String state) {
        states.remove(normalizeState(state));
    }

    int stateCount() {
        cleanupExpired();
        return states.size();
    }

    private void cleanupExpired() {
        Instant current = now();
        states.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(current));
    }

    private boolean isExpired(StoredState storedState) {
        return !storedState.expiresAt().isAfter(now());
    }

    private Instant now() {
        return clock.instant();
    }

    private static String normalizeState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("state must not be blank");
        }
        return state.trim();
    }

    private static Duration requirePositive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return ttl;
    }

    private record StoredState(UassLoginState loginState, Instant expiresAt) {
    }
}
