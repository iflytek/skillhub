package com.iflytek.skillhub.auth.settings;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LocalAuthSettingsService {
    public static final String SETTING_KEY = "auth.local";
    public static final String PASSWORD_LOGIN_KEY = "passwordLoginEnabled";
    public static final String SELF_REGISTRATION_KEY = "selfRegistrationEnabled";

    private final SystemSettingRepository repository;

    public LocalAuthSettingsService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public LocalAuthSettings current() {
        SystemSetting setting;
        try {
            setting = repository.findBySettingKey(SETTING_KEY)
                    .orElseThrow(this::unavailable);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        Map<String, Object> value = setting.getValue();
        if (!(value.get(PASSWORD_LOGIN_KEY) instanceof Boolean passwordLoginEnabled)
                || !(value.get(SELF_REGISTRATION_KEY) instanceof Boolean selfRegistrationEnabled)
                || value.size() != 2) {
            throw unavailable();
        }
        return new LocalAuthSettings(setting.getId(), passwordLoginEnabled,
                selfRegistrationEnabled, setting.getVersion(), setting.getUpdatedAt());
    }

    private AuthFlowException unavailable() {
        return new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.settings.unavailable");
    }

    public void requirePasswordLogin() {
        if (!current().passwordLoginEnabled()) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.login.disabled");
        }
    }

    public void requireSelfRegistration() {
        if (!current().registrationAvailable()) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.registration.disabled");
        }
    }
}
