package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageAccessExceptionTest {

    @Test
    void constructorAndGetters_work() {
        Throwable cause = new RuntimeException("root cause");
        StorageAccessException ex = new StorageAccessException("read", "key-1", cause);

        assertThat(ex.getOperation()).isEqualTo("read");
        assertThat(ex.getKey()).isEqualTo("key-1");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("read").contains("key-1");
    }
}
