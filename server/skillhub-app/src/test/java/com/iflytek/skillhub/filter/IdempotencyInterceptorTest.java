package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecord;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import com.iflytek.skillhub.domain.idempotency.IdempotencyStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private IdempotencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyInterceptor(idempotencyRecordRepository, redisTemplate);
    }

    @Test
    void testNewRequestPassesThrough() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-123");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(idempotencyRecordRepository.findByRequestId("req-123")).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void testDuplicateRequestReturnsCachedResponse() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-456");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(valueOperations.get("idempotency:req-456")).thenReturn("COMPLETED");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
    }

    @Test
    void testNoRequestIdHeaderPassesThrough() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(idempotencyRecordRepository, never()).findByRequestId(anyString());
    }

    @Test
    void testGetRequestPassesThrough() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testAfterCompletionUpdatesRecord() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-789");
        when(response.getStatus()).thenReturn(200);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        IdempotencyRecord record = new IdempotencyRecord(
            "req-789", null, null, IdempotencyStatus.PROCESSING, null,
            Instant.now(), Instant.now().plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-789")).thenReturn(Optional.of(record));

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
        verify(valueOperations).set(eq("idempotency:req-789"), eq("COMPLETED"), anyLong(), any(TimeUnit.class));
    }
}
