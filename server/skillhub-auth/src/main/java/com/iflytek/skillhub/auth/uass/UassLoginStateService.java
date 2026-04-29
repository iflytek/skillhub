package com.iflytek.skillhub.auth.uass;

import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.auth.uass.store.UassLoginState;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UassLoginStateService {

    private final UassLoginStateStore loginStateStore;
    private final Clock clock;
    private final Supplier<String> stateGenerator;

    public UassLoginStateService(UassLoginStateStore loginStateStore) {
        this(loginStateStore, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    UassLoginStateService(UassLoginStateStore loginStateStore, Clock clock, Supplier<String> stateGenerator) {
        this.loginStateStore = Objects.requireNonNull(loginStateStore, "loginStateStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.stateGenerator = Objects.requireNonNull(stateGenerator, "stateGenerator must not be null");
    }

    public String startLogin(String returnTo, String requestFingerprint) {
        String state = requireState(stateGenerator.get());
        loginStateStore.save(state, new UassLoginState(
                resolveReturnTo(returnTo),
                clock.instant(),
                "uass",
                normalizeOptional(requestFingerprint)
        ));
        return state;
    }

    public Optional<UassLoginState> consumeForCallback(String state) {
        return loginStateStore.consume(requireState(state));
    }

    public void clearFailedCallback(String state) {
        if (!StringUtils.hasText(state)) {
            return;
        }
        loginStateStore.delete(state.trim());
    }

    private static String resolveReturnTo(String returnTo) {
        String sanitized = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        return sanitized == null ? OAuthLoginRedirectSupport.DEFAULT_TARGET_URL : sanitized;
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("state must not be blank");
        }
        return state.trim();
    }
}
