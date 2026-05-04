package com.iflytek.skillhub.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthFlowExceptionTest {

    @Test
    void constructorPopulatesAllFieldsAndGetters() {
        AuthFlowException ex = new AuthFlowException(HttpStatus.BAD_REQUEST, "test.code", "arg1", "arg2");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("test.code");
        assertThat(ex.messageCode()).isEqualTo("test.code");
        assertThat(ex.getMessageCode()).isEqualTo("test.code");
        assertThat(ex.messageArgs()).containsExactly("arg1", "arg2");
        assertThat(ex.getMessageArgs()).containsExactly("arg1", "arg2");
    }

    @Test
    void constructorWithNoExtraArgs() {
        AuthFlowException ex = new AuthFlowException(HttpStatus.UNAUTHORIZED, "no.args");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.messageArgs()).isEmpty();
        assertThat(ex.getMessageArgs()).isEmpty();
    }
}
