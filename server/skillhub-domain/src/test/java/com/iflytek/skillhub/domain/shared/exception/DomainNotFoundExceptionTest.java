package com.iflytek.skillhub.domain.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainNotFoundExceptionTest {

    @Test
    void constructor_shouldSetMessageCode() {
        DomainNotFoundException ex = new DomainNotFoundException("error.code", "arg1");

        assertThat(ex.getMessage()).isEqualTo("error.code");
        assertThat(ex.messageCode()).isEqualTo("error.code");
        assertThat(ex.messageArgs()).containsExactly("arg1");
    }

    @Test
    void statusCode_shouldReturn404() {
        DomainNotFoundException ex = new DomainNotFoundException("error.code");

        assertThat(ex.statusCode()).isEqualTo(404);
    }
}
