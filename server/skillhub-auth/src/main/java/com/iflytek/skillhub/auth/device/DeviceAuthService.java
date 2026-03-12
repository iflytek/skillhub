package com.iflytek.skillhub.auth.device;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final SecureRandom random = new SecureRandom();

    public DeviceAuthService(RedisTemplate<String, Object> redisTemplate,
                             @Value("${skillhub.device-auth.verification-uri:/device}") String verificationUri) {
        this.redisTemplate = redisTemplate;
        this.verificationUri = verificationUri;
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

        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue().get(DEVICE_CODE_PREFIX + deviceCode);
        if (data == null) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.expired");
        }

        data.setStatus(DeviceCodeStatus.AUTHORIZED);
        data.setUserId(userId);
        redisTemplate.opsForValue().set(
            DEVICE_CODE_PREFIX + deviceCode, data, EXPIRES_IN_SECONDS / 60, TimeUnit.MINUTES);
    }

    public DeviceTokenResponse pollToken(String deviceCode) {
        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue().get(DEVICE_CODE_PREFIX + deviceCode);

        if (data == null) {
            throw new DomainBadRequestException("error.deviceAuth.deviceCode.invalid");
        }

        return switch (data.getStatus()) {
            case PENDING -> DeviceTokenResponse.pending();
            case AUTHORIZED -> {
                data.setStatus(DeviceCodeStatus.USED);
                redisTemplate.opsForValue().set(
                    DEVICE_CODE_PREFIX + deviceCode, data, 1, TimeUnit.MINUTES);
                yield DeviceTokenResponse.success(null);
            }
            case USED -> throw new DomainBadRequestException("error.deviceAuth.deviceCode.used");
        };
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
