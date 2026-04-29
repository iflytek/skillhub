package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.uass.store.UassLoginState;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UassLoginStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-29T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void startLogin_savesGeneratedStateAndNormalizedContext() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> " state-1 ");

        String state = service.startLogin(" /reports ", " fingerprint-1 ");

        ArgumentCaptor<UassLoginState> stateCaptor = ArgumentCaptor.forClass(UassLoginState.class);
        verify(store).save(org.mockito.ArgumentMatchers.eq("state-1"), stateCaptor.capture());
        assertThat(state).isEqualTo("state-1");
        assertThat(stateCaptor.getValue()).isEqualTo(new UassLoginState(
                "/reports",
                NOW,
                "uass",
                "fingerprint-1"
        ));
    }

    @Test
    void startLogin_fallsBackToDefaultTargetWhenReturnToIsUnsafe() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> "state-2");

        service.startLogin("https://evil.example.com", " ");

        ArgumentCaptor<UassLoginState> stateCaptor = ArgumentCaptor.forClass(UassLoginState.class);
        verify(store).save(org.mockito.ArgumentMatchers.eq("state-2"), stateCaptor.capture());
        assertThat(stateCaptor.getValue().returnTo()).isEqualTo("/dashboard");
        assertThat(stateCaptor.getValue().requestFingerprint()).isNull();
    }

    @Test
    void startLogin_rejectsBlankGeneratedState() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> " ");

        assertThatThrownBy(() -> service.startLogin("/dashboard", "fingerprint-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("state must not be blank");
    }

    @Test
    void consumeForCallback_delegatesToStoreUsingTrimmedState() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginState loginState = new UassLoginState("/dashboard", NOW, "uass", "fingerprint-1");
        when(store.consume("state-1")).thenReturn(Optional.of(loginState));
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> "state-1");

        assertThat(service.consumeForCallback(" state-1 ")).contains(loginState);
    }

    @Test
    void clearFailedCallback_deletesTrimmedState() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> "state-1");

        service.clearFailedCallback(" state-1 ");

        verify(store).delete("state-1");
    }

    @Test
    void clearFailedCallback_ignoresBlankState() {
        UassLoginStateStore store = mock(UassLoginStateStore.class);
        UassLoginStateService service = new UassLoginStateService(store, CLOCK, () -> "state-1");

        service.clearFailedCallback(" ");

        verifyNoInteractions(store);
    }
}
