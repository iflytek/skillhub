package com.iflytek.skillhub.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class SessionRecoveryConfigTest {

    @Test
    void corruptSessionRemover_deletesSessionKeysWithoutReadingTheCorruptValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        var remover = new SessionRecoveryConfig()
                .corruptSessionRemover(redisTemplate, "skillhub:session");

        remover.remove("broken-session");

        verify(redisTemplate).delete(List.of(
                "skillhub:session:sessions:broken-session",
                "skillhub:session:sessions:expires:broken-session"
        ));
    }
}
