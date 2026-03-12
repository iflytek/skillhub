package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecord;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import com.iflytek.skillhub.domain.idempotency.IdempotencyStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REDIS_KEY_PREFIX = "idempotency:";
    private static final long EXPIRY_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final StringRedisTemplate redisTemplate;

    public IdempotencyInterceptor(IdempotencyRecordRepository idempotencyRecordRepository,
                                  StringRedisTemplate redisTemplate) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.redisTemplate = redisTemplate;
    }

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
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", EXPIRY_HOURS, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(isNew)) {
            // Duplicate request - check status in Redis or DB
            String cachedStatus = redisTemplate.opsForValue().get(redisKey);
            if ("COMPLETED".equals(cachedStatus)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("{\"error\":\"Duplicate request\"}");
                return false;
            }
        }

        // Check PostgreSQL fallback
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                response.setStatus(record.getResponseStatusCode() != null ? record.getResponseStatusCode() : HttpServletResponse.SC_OK);
                response.getWriter().write("{\"message\":\"Request already processed\"}");
                return false;
            }
        } else {
            // Create new record
            Instant now = Instant.now();
            IdempotencyRecord newRecord = new IdempotencyRecord(
                requestId,
                null,
                null,
                IdempotencyStatus.PROCESSING,
                null,
                now,
                now.plusSeconds(EXPIRY_HOURS * 3600)
            );
            idempotencyRecordRepository.save(newRecord);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        String method = request.getMethod();
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("DELETE")) {
            return;
        }

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        // Update record with response status
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            record.setStatus(ex == null ? IdempotencyStatus.COMPLETED : IdempotencyStatus.FAILED);
            record.setResponseStatusCode(response.getStatus());
            idempotencyRecordRepository.save(record);

            // Update Redis
            String redisKey = REDIS_KEY_PREFIX + requestId;
            redisTemplate.opsForValue().set(redisKey, record.getStatus().name(), EXPIRY_HOURS, TimeUnit.HOURS);
        }
    }
}
