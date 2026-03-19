package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecord;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import com.iflytek.skillhub.domain.idempotency.IdempotencyStatus;
import com.iflytek.skillhub.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Prevents duplicate execution of mutating HTTP requests identified by
 * {@code X-Request-Id}.
 *
 * <p>Redis is treated as the fast-path cache, while PostgreSQL remains the
 * durable source of truth when cache access fails.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REDIS_KEY_PREFIX = "idempotency:";
    private static final long EXPIRY_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate,
                                  IdempotencyRecordRepository idempotencyRecordRepository,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.redisTemplate = redisTemplate;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Rejects duplicate mutating requests before controller execution and
     * creates a processing marker for first-seen request identifiers.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("DELETE")) {
            return true;
        }

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            return true;
        }

        // Check Redis first
        String redisKey = REDIS_KEY_PREFIX + requestId;
        boolean isDuplicate = false;
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            isDuplicate = "COMPLETED".equals(cached);
        } catch (Exception ignored) {
            // Redis unavailable, fall through to PostgreSQL
        }

        if (isDuplicate) {
            writeDuplicateResponse(response);
            return false;
        }

        // Check PostgreSQL fallback
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                int statusCode = record.getResponseStatusCode() != null ? record.getResponseStatusCode() : HttpServletResponse.SC_OK;
                response.setStatus(statusCode);
                writeDuplicateResponse(response);
                return false;
            }
        } else {
            // Create new record
            Instant now = Instant.now(clock);
            IdempotencyRecord newRecord = new IdempotencyRecord(
                requestId, (String) null, (Long) null, IdempotencyStatus.PROCESSING,
                (Integer) null, now, now.plusSeconds(EXPIRY_HOURS * 3600));
            idempotencyRecordRepository.save(newRecord);

            // Cache in Redis
            try {
                redisTemplate.opsForValue().set(redisKey, "PROCESSING", EXPIRY_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {
                // Redis unavailable, PostgreSQL is the source of truth
            }
        }

        return true;
    }

    /**
     * Finalizes the idempotency record with the observed response status once
     * request processing has completed.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("DELETE")) {
            return;
        }

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            record.setStatus(ex == null ? IdempotencyStatus.COMPLETED : IdempotencyStatus.FAILED);
            record.setResponseStatusCode(response.getStatus());
            idempotencyRecordRepository.save(record);

            try {
                String redisKey = REDIS_KEY_PREFIX + requestId;
                redisTemplate.opsForValue().set(redisKey, record.getStatus().name(), EXPIRY_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {
                // Redis unavailable
            }
        }
    }

    private void writeDuplicateResponse(HttpServletResponse response) throws Exception {
        ApiResponse<Void> body = new ApiResponse<>(409, "error.request.duplicate", null,
                Instant.now(clock), null);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
