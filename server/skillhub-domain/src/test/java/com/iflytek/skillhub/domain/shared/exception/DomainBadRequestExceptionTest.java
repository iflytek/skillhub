package com.iflytek.skillhub.domain.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainBadRequestExceptionTest {

    @Test
    void constructor_shouldSetMessageCode() {
        DomainBadRequestException ex = new DomainBadRequestException("error.code", "arg1");

        assertThat(ex.getMessage()).isEqualTo("error.code");
        assertThat(ex.messageCode()).isEqualTo("error.code");
        assertThat(ex.messageArgs()).containsExactly("arg1");
    }

    @Test
    void statusCode_shouldReturn400() {
        DomainBadRequestException ex = new DomainBadRequestException("error.code");

        assertThat(ex.statusCode()).isEqualTo(400);
    }
}
