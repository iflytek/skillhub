package com.iflytek.skillhub.domain.setting;

import java.util.Optional;

public interface SystemSettingRepository {
    Optional<SystemSetting> findBySettingKey(String settingKey);
    SystemSetting save(SystemSetting setting);
}
