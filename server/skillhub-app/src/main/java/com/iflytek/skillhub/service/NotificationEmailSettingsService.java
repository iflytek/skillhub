package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.settings.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.notification.email.*;
import jakarta.persistence.*;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailSettingsService implements NotificationEmailPolicyProvider, ApplicationRunner {
    public static final String SETTING_KEY = "notification.email";
    private final SystemSettingRepository settings;
    private final EntityManager entityManager;
    private final boolean initialEnabled;
    private final int initialWindow;
    private final String initialUrl;
    private final String smtpHost;
    private final String fromAddress;
    private final com.iflytek.skillhub.domain.audit.AuditLogService audit;
    private final com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation cancellation;
    public NotificationEmailSettingsService(SystemSettingRepository settings, EntityManager entityManager,
            @Value("${skillhub.notification.email.enabled:false}") boolean enabled,
            @Value("${skillhub.notification.email.digest-window-minutes:60}") int window,
            @Value("${skillhub.notification.email.public-base-url:}") String url,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${skillhub.notification.email.from-address:${spring.mail.username:}}") String fromAddress,
            com.iflytek.skillhub.domain.audit.AuditLogService audit,
            com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation cancellation) {
        this.settings = settings;
        this.entityManager = entityManager;
        this.initialEnabled = enabled;
        this.initialWindow = window;
        this.initialUrl = url;
        this.smtpHost = smtpHost;
        this.fromAddress = fromAddress;
        this.audit = audit;
        this.cancellation = cancellation;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (settings.findBySettingKey(SETTING_KEY).isPresent())
            return;
        // Match the existing auth initializer: serialize replica seeding on PostgreSQL,
        // while keeping the H2 application-test profile supported.
        entityManager.unwrap(org.hibernate.Session.class).doWork(connection -> {
            if ("PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(97820261022)");
                }
            }
        });
        if (settings.findBySettingKey(SETTING_KEY).isPresent()) {
            return;
        }
        validate(initialEnabled, initialWindow, initialUrl);
        settings.saveAndFlush(new SystemSetting(SETTING_KEY,
                Map.of("enabled", initialEnabled, "digestWindowMinutes", initialWindow, "publicBaseUrl", initialUrl)));
    }
    @Override
    @Transactional(readOnly = true)
    public NotificationEmailPolicy current() {
        return policy(load(false));
    }
    @Override
    @Transactional
    public NotificationEmailPolicy lockCurrent() {
        return policy(load(true));
    }
    private SystemSetting load(boolean lock) {
        var query =
                entityManager.createQuery("select s from SystemSetting s where s.settingKey=:key", SystemSetting.class)
                        .setParameter("key", SETTING_KEY);
        if (lock)
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        var setting = query.getSingleResult();
        if (lock)
            entityManager.refresh(setting, LockModeType.PESSIMISTIC_WRITE);
        return setting;
    }
    private NotificationEmailPolicy policy(SystemSetting s) {
        var values = s.getValue();
        if (!(values.get("enabled") instanceof Boolean enabled)
                || !(values.get("digestWindowMinutes") instanceof Number window)
                || !(values.get("publicBaseUrl") instanceof String url))
            throw new IllegalStateException("Invalid notification email settings");
        return new NotificationEmailPolicy(enabled, window.intValue(), url, s.getVersion());
    }
    public boolean smtpConfigured() {
        return smtpHost != null && !smtpHost.isBlank() && fromAddress != null
                && fromAddress.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }
    @Transactional
    public NotificationEmailPolicy update(boolean enabled, int window, String url, long version, String actor) {
        validate(enabled, window, url);
        var setting = load(true);
        if (setting.getVersion() != version)
            throw new DomainConflictException("error.system.settings.version.conflict");
        setting.update(Map.of("enabled", enabled, "digestWindowMinutes", window, "publicBaseUrl", url), actor);
        settings.saveAndFlush(setting);
        if (!enabled)
            cancellation.cancelAll();
        audit.record(
                actor, "NOTIFICATION_EMAIL_SETTINGS_UPDATE", "SYSTEM_SETTING", setting.getId(), null, null, null, null);
        return policy(setting);
    }
    private void validate(boolean enabled, int window, String url) {
        if (window < 5 || window > 1440)
            throw new DomainBadRequestException("error.notification.email.invalidSettings");
        if (enabled && !smtpConfigured())
            throw new DomainBadRequestException("error.notification.email.smtpUnavailable");
        if (url == null || url.isBlank()) {
            if (enabled)
                throw new DomainBadRequestException("error.notification.email.invalidSettings");
            return;
        }
        try {
            URI uri = URI.create(url);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.notification.email.invalidSettings");
        }
    }
}
