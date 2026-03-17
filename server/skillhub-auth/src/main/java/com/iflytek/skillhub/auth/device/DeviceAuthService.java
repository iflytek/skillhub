package com.iflytek.skillhub.auth.device;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceAuthService {

    private static final String DEVICE_CODE_PREFIX = "device:code:";
    private static final String DEVICE_CLAIM_PREFIX = "device:claim:";
    private static final String USER_CODE_PREFIX = "device:usercode:";
    private static final int EXPIRES_IN_SECONDS = 900;
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final String USER_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long PENDING_CODE_TTL_MINUTES = EXPIRES_IN_SECONDS / 60L;
    private static final long USED_CODE_TTL_MINUTES = 1L;
    private static final String CLI_DEVICE_TOKEN_NAME = "CLI Device Flow";
    private static final String CLI_DEVICE_SCOPE_JSON = "[\"skill:read\",\"skill:publish\"]";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiTokenService apiTokenService;
    private final String verificationUri;
    private final SecureRandom random = new SecureRandom();

    public DeviceAuthService(RedisTemplate<String, Object> redisTemplate,
                             ApiTokenService apiTokenService,
                             @Value("${skillhub.device-auth.verification-uri:/cli/auth}") String verificationUri) {
        this.redisTemplate = redisTemplate;
        this.apiTokenService = apiTokenService;
        this.verificationUri = verificationUri;
    }

    public DeviceCodeResponse generateDeviceCode() {
        String deviceCode = generateRandomDeviceCode();
        String userCode = generateUserCode();

        DeviceCodeData data = new DeviceCodeData(deviceCode, userCode, DeviceCodeStatus.PENDING, null);

        redisTemplate.opsForValue().set(
            DEVICE_CODE_PREFIX + deviceCode, data, PENDING_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(
            USER_CODE_PREFIX + userCode, deviceCode, PENDING_CODE_TTL_MINUTES, TimeUnit.MINUTES);

        return new DeviceCodeResponse(deviceCode, userCode, verificationUri, EXPIRES_IN_SECONDS, POLL_INTERVAL_SECONDS);
    }

    public void authorizeDeviceCode(String userCode, String userId) {
        String deviceCode = (String) redisTemplate.opsForValue().get(USER_CODE_PREFIX + userCode);
        if (deviceCode == null) {
            throw new DomainBadRequestException("error.deviceAuth.userCode.invalid");
        }

        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue().get(DEVICE_CODE_PREFIX + deviceCode);
        if (data == null) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.expired");
        }

        switch (data.getStatus()) {
            case PENDING -> {
                data.setStatus(DeviceCodeStatus.AUTHORIZED);
                data.setUserId(userId);
                redisTemplate.opsForValue().set(
                    DEVICE_CODE_PREFIX + deviceCode, data, PENDING_CODE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            case AUTHORIZED -> {
                if (!userId.equals(data.getUserId())) {
                    throw new DomainBadRequestException("error.deviceAuth.deviceCode.alreadyAuthorized");
                }
            }
            case USED -> throw new DomainBadRequestException("error.deviceAuth.deviceCode.used");
        }
    }

    public DeviceTokenResponse pollToken(String deviceCode) {
        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue().get(DEVICE_CODE_PREFIX + deviceCode);

        if (data == null) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.invalid");
        }

        return switch (data.getStatus()) {
            case PENDING -> DeviceTokenResponse.pending();
            case AUTHORIZED -> redeemAuthorizedDeviceCode(deviceCode, data);
            case USED -> throw new DomainBadRequestException("error.deviceAuth.deviceCode.used");
        };
    }

    private DeviceTokenResponse redeemAuthorizedDeviceCode(String deviceCode, DeviceCodeData data) {
        boolean claimed = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
            DEVICE_CLAIM_PREFIX + deviceCode,
            "claimed",
            USED_CODE_TTL_MINUTES,
            TimeUnit.MINUTES
        ));
        if (!claimed) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.used");
        }

        try {
            if (!StringUtils.hasText(data.getUserId())) {
                throw new DomainBadRequestException("error.deviceAuth.deviceCode.invalid");
            }

            String token = apiTokenService.rotateToken(
                data.getUserId(),
                CLI_DEVICE_TOKEN_NAME,
                CLI_DEVICE_SCOPE_JSON
            ).rawToken();

            data.setStatus(DeviceCodeStatus.USED);
            redisTemplate.opsForValue().set(
                DEVICE_CODE_PREFIX + deviceCode,
                data,
                USED_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
            );
            redisTemplate.delete(USER_CODE_PREFIX + data.getUserCode());
            return DeviceTokenResponse.success(token);
        } catch (RuntimeException ex) {
            redisTemplate.delete(DEVICE_CLAIM_PREFIX + deviceCode);
            throw ex;
        }
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
}
