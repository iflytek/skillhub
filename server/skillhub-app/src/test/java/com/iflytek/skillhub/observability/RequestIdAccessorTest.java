package com.iflytek.skillhub.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RequestIdAccessorTest {

    private final RequestIdAccessor accessor = new RequestIdAccessor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldMirrorRequestIdToMdcAndClearItWhenScopeCloses() {
        assertThat(accessor.current()).isNull();
        assertThat(MDC.get(RequestIdAccessor.MDC_KEY)).isNull();

        try (RequestIdAccessor.Scope ignored = accessor.open("req-123")) {
            assertThat(accessor.current()).isEqualTo("req-123");
            assertThat(MDC.get(RequestIdAccessor.MDC_KEY)).isEqualTo("req-123");
        }

        assertThat(accessor.current()).isNull();
        assertThat(MDC.get(RequestIdAccessor.MDC_KEY)).isNull();
    }

    @Test
    void shouldRestoreOuterScope() {
        try (RequestIdAccessor.Scope ignored = accessor.open("outer")) {
            try (RequestIdAccessor.Scope nested = accessor.open("inner")) {
                assertThat(accessor.current()).isEqualTo("inner");
            }
            assertThat(accessor.current()).isEqualTo("outer");
            assertThat(MDC.get(RequestIdAccessor.MDC_KEY)).isEqualTo("outer");
        }
    }

    @Test
    void shouldUseThreadLocalAsAuthorityWhenMdcIsChangedExternally() {
        try (RequestIdAccessor.Scope ignored = accessor.open("authoritative")) {
            MDC.put(RequestIdAccessor.MDC_KEY, "logging-only");

            assertThat(accessor.current()).isEqualTo("authoritative");
        }

        assertThat(accessor.current()).isNull();
        assertThat(MDC.get(RequestIdAccessor.MDC_KEY)).isNull();
    }

    @Test
    void shouldRejectBlankRequestId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> accessor.open(" "));
    }
}
