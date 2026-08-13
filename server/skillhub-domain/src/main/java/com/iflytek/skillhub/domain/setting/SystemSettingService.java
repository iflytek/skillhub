package com.iflytek.skillhub.domain.setting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads and writes operator-configurable setting groups.
 *
 * <p>Every read carries the caller's defaults so that a deployment which has never touched a group
 * — or which configures it entirely through {@code application.yml} — behaves exactly as it did
 * before the group existed.
 */
@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Returns the stored group, or {@code defaults} when the group has never been overridden.
     *
     * <p>A stored document that can no longer be parsed also falls back to {@code defaults}: a
     * malformed row must not take down the flows that read settings, such as login.
     */
    @Transactional(readOnly = true)
    public <T> T get(String settingKey, Class<T> type, T defaults) {
        Optional<SystemSetting> stored = systemSettingRepository.findBySettingKey(settingKey);
        if (stored.isEmpty()) {
            return defaults;
        }
        try {
            return objectMapper.readValue(stored.get().getSettingValue(), type);
        } catch (Exception e) {
            log.warn("Falling back to defaults for system setting '{}': stored value is not readable as {}",
                    settingKey, type.getSimpleName(), e);
            return defaults;
        }
    }

    /**
     * Overwrites a setting group and records who changed it.
     */
    @Transactional
    public <T> T put(String settingKey, T value, String updatedBy) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("System setting '" + settingKey + "' is not serializable", e);
        }
        Instant now = Instant.now(clock);
        SystemSetting setting = systemSettingRepository.findBySettingKey(settingKey)
                .orElseGet(() -> new SystemSetting(settingKey, json, updatedBy, now));
        setting.setSettingValue(json);
        setting.setUpdatedBy(updatedBy);
        setting.setUpdatedAt(now);
        systemSettingRepository.save(setting);
        return value;
    }
}
