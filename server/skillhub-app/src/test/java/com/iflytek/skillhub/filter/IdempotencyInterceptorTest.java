package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private IdempotencyInterceptor interceptor;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        interceptor = new IdempotencyInterceptor(redisTemplateProvider, idempotencyRecordRepository, objectMapper, clock);
    }

    @Test
    void testNewRequestPassesThrough() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-123");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:req-123")).thenReturn(null);
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
        when(valueOperations.get("idempotency:req-456")).thenReturn("COMPLETED");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("error.request.duplicate"));
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
            "req-789", (String) null, (Long) null, IdempotencyStatus.PROCESSING, (Integer) null,
            Instant.now(clock), Instant.now(clock).plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-789")).thenReturn(Optional.of(record));

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
        verify(valueOperations).set(eq("idempotency:req-789"), eq("COMPLETED"), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testNoRedisAvailable_usesDatabaseOnly() throws Exception {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        interceptor = new IdempotencyInterceptor(redisTemplateProvider, idempotencyRecordRepository, new ObjectMapper(), clock);

        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-noredis");
        when(idempotencyRecordRepository.findByRequestId("req-noredis")).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testDatabaseRecordProcessingStatus_allowsRequestToProceed() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-processing");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:req-processing")).thenReturn(null);

        IdempotencyRecord record = new IdempotencyRecord(
            "req-processing", (String) null, (Long) null, IdempotencyStatus.PROCESSING, (Integer) null,
            Instant.now(clock), Instant.now(clock).plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-processing")).thenReturn(Optional.of(record));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    void testAfterCompletionWithException_setsFailedStatus() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-fail");
        when(response.getStatus()).thenReturn(500);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        IdempotencyRecord record = new IdempotencyRecord(
            "req-fail", (String) null, (Long) null, IdempotencyStatus.PROCESSING, (Integer) null,
            Instant.now(clock), Instant.now(clock).plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-fail")).thenReturn(Optional.of(record));

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        verify(idempotencyRecordRepository).save(argThat(r -> r.getStatus() == IdempotencyStatus.FAILED));
        verify(valueOperations).set(eq("idempotency:req-fail"), eq("FAILED"), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testAfterCompletionGetRequest_returnsEarly() {
        when(request.getMethod()).thenReturn("GET");

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository, never()).findByRequestId(anyString());
    }

    @Test
    void testAfterCompletionNoRequestId_returnsEarly() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository, never()).findByRequestId(anyString());
    }

    @Test
    void testAfterCompletionNoRecordFound_doesNothing() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-none");
        when(idempotencyRecordRepository.findByRequestId("req-none")).thenReturn(Optional.empty());

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository, never()).save(any());
    }

    @Test
    void testRedisExceptionDuringPreHandle_fallsThroughToDatabase() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-redis-err");
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(idempotencyRecordRepository.findByRequestId("req-redis-err")).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void testRedisExceptionDuringAfterCompletion_ignoresError() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-redis-err2");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        IdempotencyRecord record = new IdempotencyRecord(
            "req-redis-err2", (String) null, (Long) null, IdempotencyStatus.PROCESSING, (Integer) null,
            Instant.now(clock), Instant.now(clock).plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-redis-err2")).thenReturn(Optional.of(record));

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void testDuplicateFromDatabaseWithStatusCode_writesCorrectStatus() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Request-Id")).thenReturn("req-db-dup");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:req-db-dup")).thenReturn(null);

        IdempotencyRecord record = new IdempotencyRecord(
            "req-db-dup", (String) null, (Long) null, IdempotencyStatus.COMPLETED, 201,
            Instant.now(clock), Instant.now(clock).plusSeconds(86400)
        );
        when(idempotencyRecordRepository.findByRequestId("req-db-dup")).thenReturn(Optional.of(record));

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).setStatus(201);
    }
}
