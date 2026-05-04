package com.iflytek.skillhub.infra.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpClientExceptionTest {

    @Test
    void constructorWithStatusAndBody() {
        HttpClientException ex = new HttpClientException(502, "bad gateway");

        assertThat(ex.getStatusCode()).isEqualTo(502);
        assertThat(ex.getResponseBody()).isEqualTo("bad gateway");
        assertThat(ex.getMessage()).isEqualTo("HTTP 502: bad gateway");
    }

    @Test
    void constructorWithMessageAndCause() {
        Throwable cause = new RuntimeException("network error");
        HttpClientException ex = new HttpClientException("connection failed", cause);

        assertThat(ex.getStatusCode()).isEqualTo(0);
        assertThat(ex.getResponseBody()).isNull();
        assertThat(ex.getMessage()).isEqualTo("connection failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
