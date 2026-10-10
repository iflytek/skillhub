package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

class AsyncConfigTest {

    @Test
    void asyncConfig_enablesAsyncAndScheduling() {
        assertThat(AsyncConfig.class).hasAnnotation(EnableAsync.class);
        assertThat(AsyncConfig.class).hasAnnotation(EnableScheduling.class);
    }
}
