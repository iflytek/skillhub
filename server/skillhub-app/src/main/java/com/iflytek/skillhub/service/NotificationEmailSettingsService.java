package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.settings.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.notification.email.*;
import jakarta.persistence.*;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailSettingsService implements NotificationEmailPolicyProvider, ApplicationRunner {
    public static final String SETTING_KEY = "notification.email";
    private final Clock clock;
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
            com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation cancellation, Clock clock) {
        this.clock = clock;
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
        var existing = settings.findBySettingKey(SETTING_KEY);
        if (existing.isPresent() && (!Boolean.TRUE.equals(existing.get().getValue().get("enabled"))
                || existing.get().getValue().get("enabledSince") != null))
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
            var setting = load(LockModeType.PESSIMISTIC_WRITE);
            // Existing deployments receive a single startup baseline; reads never manufacture a new watermark.
            if (Boolean.TRUE.equals(setting.getValue().get("enabled"))
                    && setting.getValue().get("enabledSince") == null) {
                var values = new HashMap<String, Object>(setting.getValue());
                values.put("enabledSince", Instant.now(clock).toString());
                setting.update(values, null);
                settings.saveAndFlush(setting);
            }
            return;
        }
        validate(initialEnabled, initialWindow, initialUrl);
        var values = new HashMap<String, Object>();
        values.put("enabled", initialEnabled);
        values.put("digestWindowMinutes", initialWindow);
        values.put("publicBaseUrl", initialUrl);
        if (initialEnabled)
            values.put("enabledSince", Instant.now(clock).toString());
        settings.saveAndFlush(new SystemSetting(SETTING_KEY, values));
    }
    @Override
    @Transactional(readOnly = true)
    public NotificationEmailPolicy current() {
        return policy(load(LockModeType.NONE));
    }
    @Override
    @Transactional
    public NotificationEmailPolicy lockCurrent() {
        return policy(load(LockModeType.PESSIMISTIC_READ));
    }
    private SystemSetting load(LockModeType lockMode) {
        var query =
                entityManager.createQuery("select s from SystemSetting s where s.settingKey=:key", SystemSetting.class)
                        .setParameter("key", SETTING_KEY);
        var setting = query.getSingleResult();
        // Lock via refresh: upgrading a query lock can reject a cached old @Version before refresh runs.
        // Policy decisions and update version checks happen only after this locked database reload.
        if (lockMode != LockModeType.NONE)
            entityManager.refresh(setting, lockMode);
        return setting;
    }
    private NotificationEmailPolicy policy(SystemSetting s) {
        var values = s.getValue();
        if (!(values.get("enabled") instanceof Boolean enabled)
                || !(values.get("digestWindowMinutes") instanceof Number window)
                || !(values.get("publicBaseUrl") instanceof String url))
            throw new IllegalStateException("Invalid notification email settings");
        var since = values.get("enabledSince");
        return new NotificationEmailPolicy(enabled, window.intValue(), url, s.getVersion(),
                since instanceof String timestamp ? Instant.parse(timestamp) : null);
    }
    public boolean smtpConfigured() {
        return smtpHost != null && !smtpHost.isBlank() && fromAddress != null
                && fromAddress.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }
    @Transactional
    public NotificationEmailPolicy update(boolean enabled, int window, String url, long version, String actor) {
        validate(enabled, window, url);
        var setting = load(LockModeType.PESSIMISTIC_WRITE);
        if (setting.getVersion() != version)
            throw new DomainConflictException("error.system.settings.version.conflict");
        var values = new HashMap<String, Object>(setting.getValue());
        if (enabled && !Boolean.TRUE.equals(values.get("enabled")))
            values.put("enabledSince", Instant.now(clock).toString());
        values.put("enabled", enabled);
        values.put("digestWindowMinutes", window);
        values.put("publicBaseUrl", url);
        setting.update(values, actor);
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
