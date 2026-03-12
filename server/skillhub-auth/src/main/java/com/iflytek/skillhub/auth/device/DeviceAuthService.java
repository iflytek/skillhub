package com.iflytek.skillhub.auth.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceAuthService {

    private static final String DEVICE_CODE_PREFIX = "device:code:";
    private static final String USER_CODE_PREFIX = "device:usercode:";
    private static final int EXPIRES_IN_SECONDS = 900;
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final String USER_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String verificationUri;
    private final ObjectMapper objectMapper;
    private final ApiTokenService apiTokenService;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public DeviceAuthService(RedisTemplate<String, Object> redisTemplate,
                             ApiTokenService apiTokenService,
                             @Value("${skillhub.device-auth.verification-uri:/device}") String verificationUri) {
        this(redisTemplate, apiTokenService, verificationUri, new ObjectMapper());
    }

    public DeviceAuthService(RedisTemplate<String, Object> redisTemplate,
                             ApiTokenService apiTokenService,
                             String verificationUri,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.verificationUri = verificationUri;
        this.objectMapper = objectMapper;
        this.apiTokenService = apiTokenService;
    }

    public DeviceCodeResponse generateDeviceCode() {
        String deviceCode = generateRandomDeviceCode();
        String userCode = generateUserCode();

        DeviceCodeData data = new DeviceCodeData(deviceCode, userCode, DeviceCodeStatus.PENDING, null);

        redisTemplate.opsForValue().set(
            DEVICE_CODE_PREFIX + deviceCode, data, EXPIRES_IN_SECONDS / 60, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(
            USER_CODE_PREFIX + userCode, deviceCode, EXPIRES_IN_SECONDS / 60, TimeUnit.MINUTES);

        return new DeviceCodeResponse(deviceCode, userCode, verificationUri, EXPIRES_IN_SECONDS, POLL_INTERVAL_SECONDS);
    }

    public void authorizeDeviceCode(String userCode, String userId) {
        String deviceCode = (String) redisTemplate.opsForValue().get(USER_CODE_PREFIX + userCode);
        if (deviceCode == null) {
            throw new DomainBadRequestException("error.deviceAuth.userCode.invalid");
        }

        DeviceCodeData data = readDeviceCodeData(deviceCode);
        if (data == null) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.expired");
        }

        data.setStatus(DeviceCodeStatus.AUTHORIZED);
        data.setUserId(userId);
        redisTemplate.opsForValue().set(
            DEVICE_CODE_PREFIX + deviceCode, data, EXPIRES_IN_SECONDS / 60, TimeUnit.MINUTES);
    }

    public DeviceTokenResponse pollToken(String deviceCode) {
        String key = DEVICE_CODE_PREFIX + deviceCode;
        DeviceCodeData consumed = redisTemplate.execute(new SessionCallback<>() {
            @Override
            public DeviceCodeData execute(RedisOperations operations) {
                while (true) {
                    operations.watch(key);
                    DeviceCodeData data = readDeviceCodeData(operations.opsForValue(), deviceCode);

                    if (data == null) {
                        operations.unwatch();
                        throw new DomainBadRequestException("error.deviceAuth.deviceCode.invalid");
                    }

                    switch (data.getStatus()) {
                        case PENDING -> {
                            operations.unwatch();
                            return null;
                        }
                        case USED -> {
                            operations.unwatch();
                            throw new DomainBadRequestException("error.deviceAuth.deviceCode.used");
                        }
                        case AUTHORIZED -> {
                            data.setStatus(DeviceCodeStatus.USED);
                            operations.multi();
                            operations.opsForValue().set(key, data, 1, TimeUnit.MINUTES);
                            if (operations.exec() != null) {
                                return data;
                            }
                        }
                    }
                }
            }
        });

        if (consumed == null) {
            return DeviceTokenResponse.pending();
        }

        String token = apiTokenService.createToken(
                consumed.getUserId(), "device-auth", "[]").rawToken();
        return DeviceTokenResponse.success(token);
    }

    private String generateRandomDeviceCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUserCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) code.append('-');
            code.append(USER_CODE_CHARS.charAt(random.nextInt(USER_CODE_CHARS.length())));
        }
        return code.toString();
    }

    private DeviceCodeData readDeviceCodeData(String deviceCode) {
        return readDeviceCodeData(redisTemplate.opsForValue(), deviceCode);
    }

    private DeviceCodeData readDeviceCodeData(ValueOperations<String, Object> valueOperations, String deviceCode) {
        Object raw = valueOperations.get(DEVICE_CODE_PREFIX + deviceCode);
        if (raw == null) {
            return null;
        }
        if (raw instanceof DeviceCodeData data) {
            return data;
        }
        return objectMapper.convertValue(raw, DeviceCodeData.class);
    }
}
