package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.setting.SystemSetting;
import com.iflytek.skillhub.domain.setting.SystemSettingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingJpaRepository extends JpaRepository<SystemSetting, String>, SystemSettingRepository {
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
