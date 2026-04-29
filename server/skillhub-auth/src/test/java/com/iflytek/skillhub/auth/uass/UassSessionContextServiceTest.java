package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UassSessionContextServiceTest {

    private static final UassLoginContext LOGIN_CONTEXT = new UassLoginContext(
            "state-1",
            URI.create("https://skillhub.example.com/api/v1/auth/uass/callback"),
            "U1001",
            "access-token",
            "refresh-token",
            Instant.parse("2026-04-30T08:00:00Z"),
            Map.of("tenant", "acme")
    );

    private final UassSessionContextService service = new UassSessionContextService();

    @Test
    void bindAndLoad_roundTripsNormalizedLoginContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        service.bind(LOGIN_CONTEXT, request);

        assertThat(service.load(request)).contains(LOGIN_CONTEXT);
    }

    @Test
    void clear_removesStoredSnapshot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.bind(LOGIN_CONTEXT, request);

        service.clear(request);

        assertThat(service.load(request)).isEmpty();
    }
}
