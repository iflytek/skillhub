package com.iflytek.skillhub.security;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFailureThrottleServiceTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void assertAllowed_throttlesIdentifierInMemoryAfterEightFailures() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "memory");

        for (int i = 0; i < 8; i++) {
            service.recordFailure("local", "Alice", "203.0.113.10");
        }

        assertThatThrownBy(() -> service.assertAllowed("local", " alice ", "198.51.100.1"))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(error -> {
                    AuthFlowException exception = (AuthFlowException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getMessageCode()).isEqualTo("error.auth.login.throttled");
                    assertThat((Long) exception.getMessageArgs()[0]).isGreaterThanOrEqualTo(1L);
                });
    }

    @Test
    void assertAllowed_throttlesIpInMemoryAfterThirtyFailures() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "memory");

        for (int i = 0; i < 30; i++) {
            service.recordFailure("local", null, "203.0.113.10");
        }

        assertThatThrownBy(() -> service.assertAllowed("local", null, "203.0.113.10"))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(error -> {
                    AuthFlowException exception = (AuthFlowException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getMessageCode()).isEqualTo("error.auth.login.throttled");
                });
    }

    @Test
    void resetIdentifier_clearsMemoryFailures() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "memory");

        for (int i = 0; i < 8; i++) {
            service.recordFailure("local", "Alice", "203.0.113.10");
        }

        service.resetIdentifier("local", "Alice");

        assertThatCode(() -> service.assertAllowed("local", "Alice", "203.0.113.10"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_deletesMalformedRedisCounterAndAllowsRequest() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "redis");
        String key = "auth-failure:local:id:alice";
        when(valueOperations.get(key)).thenReturn("not-a-number");

        assertThatCode(() -> service.assertAllowed("local", "Alice", null))
                .doesNotThrowAnyException();

        verify(redisTemplate).delete(key);
    }

    @Test
    void recordFailure_setsExpiryOnlyForFreshRedisKeys() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "redis");
        String identifierKey = "auth-failure:local:id:alice";
        String ipKey = "auth-failure:local:ip:203.0.113.10";
        when(valueOperations.increment(identifierKey)).thenReturn(1L);
        when(valueOperations.increment(ipKey)).thenReturn(2L);

        service.recordFailure("local", "Alice", "203.0.113.10");

        verify(valueOperations).increment(identifierKey);
        verify(valueOperations).increment(ipKey);
        verify(redisTemplate).expire(identifierKey, Duration.ofMinutes(15));
        verify(redisTemplate, never()).expire(ipKey, Duration.ofMinutes(15));
    }

    @Test
    void resetIdentifier_deletesRedisKeyWhenPresent() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        AuthFailureThrottleService service = new AuthFailureThrottleService(redisTemplateProvider, "redis");

        service.resetIdentifier("local", "Alice");

        verify(redisTemplate).delete("auth-failure:local:id:alice");
    }
}
