package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationEmailContactService implements NotificationEmailRecipientProvider {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccountRepository accounts;
    private final NotificationEmailContactRepository contacts;
    private final NotificationEmailPreferenceRepository preferences;
    private final NotificationEmailPreferenceService modes;
    private final NotificationEmailSettingsService settings;
    private final JavaMailSender sender;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final String from;
    private final String fromName;
    private final java.time.Clock clock;
    private final com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation cancellation;
    public NotificationEmailContactService(UserAccountRepository accounts, NotificationEmailContactRepository contacts,
            NotificationEmailPreferenceRepository preferences, NotificationEmailPreferenceService modes,
            NotificationEmailSettingsService settings, JavaMailSender sender, PasswordEncoder encoder,
            PlatformTransactionManager manager, java.time.Clock clock,
            com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation cancellation,
            @Value("${skillhub.notification.email.from-address:${spring.mail.username:}}") String from,
            @Value("${skillhub.notification.email.from-name:SkillHub}") String fromName) {
        this.accounts = accounts;
        this.contacts = contacts;
        this.preferences = preferences;
        this.modes = modes;
        this.settings = settings;
        this.sender = sender;
        this.encoder = encoder;
        this.transaction = new TransactionTemplate(manager);
        this.from = from;
        this.fromName = fromName;
        this.clock = clock;
        this.cancellation = cancellation;
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationEmailRecipient> current(String id) {
        return accounts.findById(id)
                .filter(this::eligible)
                .flatMap(user
                        -> contacts.findById(id)
                                .filter(c -> c.verified(user.getEmail(), user.getEmailRevision()))
                                .map(c
                                        -> new NotificationEmailRecipient(id,
                                                UserAccount.normalizeEmail(user.getEmail()), user.getEmailRevision(),
                                                c.getLocale())));
    }
    @Override
    @Transactional
    public Optional<NotificationEmailRecipient> lockCurrent(String id) {
        return accounts.findLockedFreshById(id).filter(this::eligible).flatMap(user -> {
            var c = contacts.lockOrCreate(id);
            return c.verified(user.getEmail(), user.getEmailRevision())
                    ? Optional.of(new NotificationEmailRecipient(
                              id, UserAccount.normalizeEmail(user.getEmail()), user.getEmailRevision(), c.getLocale()))
                    : Optional.empty();
        });
    }
    @Transactional(readOnly = true)
    public NotificationEmailContactResponse status(String id) {
        var policy = settings.current();
        var user = accounts.findById(id).orElseThrow(() -> new DomainNotFoundException("error.admin.user.notFound"));
        var contact = contacts.findById(id).orElse(null);
        boolean verified =
                eligible(user) && contact != null && contact.verified(user.getEmail(), user.getEmailRevision());
        String reason = !eligible(user)      ? "NO_ELIGIBLE_EMAIL"
                : !policy.enabled()          ? "DISABLED"
                : !settings.smtpConfigured() ? "SMTP_UNAVAILABLE"
                : !verified                  ? "UNVERIFIED"
                                             : null;
        return new NotificationEmailContactResponse(mask(UserAccount.normalizeEmail(user.getEmail())), verified,
                reason == null, contact == null ? "en" : contact.getLocale(),
                contact == null ? null : contact.getResendAvailableAt(), reason);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public NotificationEmailContactResponse requestCode(String id, String locale) {
        if (!Set.of("zh", "en").contains(locale == null ? "" : locale))
            throw new DomainBadRequestException("error.notification.email.invalidLocale");
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String challengeId = UUID.randomUUID().toString();
        String hash = encoder.encode(codeInput(id, code));
        String email = transaction.execute(tx -> {
            requireEnabled();
            var user = lockedEligible(id);
            var contact = contacts.lockOrCreate(id);
            contact.challenge(challengeId, hash, user.getEmail(), user.getEmailRevision(), locale, Instant.now(clock));
            contacts.save(contact);
            return UserAccount.normalizeEmail(user.getEmail());
        });
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName == null || fromName.isBlank() ? from : fromName + " <" + from + ">");
        message.setTo(email);
        message.setSubject(
                "zh".equals(locale) ? "SkillHub 通知邮箱验证码" : "SkillHub notification email verification");
        message.setText("zh".equals(locale) ? "通知收件验证码：" + code + "。10 分钟内有效；不能用于登录或重置密码。"
                                              : "Notification email verification code: " + code
                                + ". Expires in 10 minutes. Not valid for login or password reset.");
        try {
            sender.send(message);
        } catch (Exception failure) {
            transaction.executeWithoutResult(tx -> {
                accounts.findLockedFreshById(id).ifPresent(u -> {
                    var c = contacts.lockOrCreate(id);
                    c.failSend(challengeId);
                    contacts.save(c);
                });
            });
            throw new com.iflytek.skillhub.auth.exception.AuthFlowException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "error.notification.email.sendFailed");
        }
        return status(id);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public NotificationEmailContactResponse verify(String id, String code) {
        boolean valid = Boolean.TRUE.equals(transaction.execute(tx -> {
            requireEnabled();
            var user = lockedEligible(id);
            var contact = contacts.lockOrCreate(id);
            if (!contact.accepts(user.getEmail(), user.getEmailRevision(), Instant.now(clock)))
                return false;
            if (code == null || !code.matches("[0-9]{6}")
                    || !encoder.matches(codeInput(id, code), contact.getChallengeHash())) {
                contact.wrongCode();
                contacts.save(contact);
                return false;
            }
            contact.consume(Instant.now(clock));
            contacts.save(contact);
            return true;
        }));
        if (!valid)
            throw new DomainBadRequestException("error.notification.email.invalidCode");
        return status(id);
    }
    @Transactional(readOnly = true)
    public List<NotificationEmailPreferenceResponse> preferences(String id) {
        return response(modes.current(id));
    }
    @Transactional
    public List<NotificationEmailPreferenceResponse> updatePreferences(
            String id, NotificationEmailPreferenceUpdateRequest request) {
        if (request == null || request.preferences() == null
                || request.preferences().size() > NotificationCategory.values().length)
            throw new DomainBadRequestException("error.notification.email.invalidPreferences");
        var changes = new EnumMap<NotificationCategory, NotificationEmailMode>(NotificationCategory.class);
        try {
            for (var item : request.preferences()) {
                var category = NotificationCategory.valueOf(item.category());
                var mode = NotificationEmailMode.valueOf(item.mode());
                if (changes.put(category, mode) != null)
                    throw new IllegalArgumentException();
            }
        } catch (Exception invalid) {
            throw new DomainBadRequestException("error.notification.email.invalidPreferences");
        }
        var policy = settings.lockCurrent();
        var recipient = lockCurrent(id);
        if (changes.values().stream().anyMatch(mode -> mode != NotificationEmailMode.OFF)
                && (!policy.enabled() || recipient.isEmpty()))
            throw new DomainBadRequestException("error.notification.email.verificationRequired");
        var existing = preferences.lockAll(id);
        for (var entry : changes.entrySet()) {
            var row = existing.stream()
                              .filter(p -> p.getCategory() == entry.getKey())
                              .findFirst()
                              .orElseGet(() -> new NotificationEmailPreference(id, entry.getKey()));
            row.setMode(entry.getValue());
            preferences.save(row);
        }
        var off = changes.entrySet()
                          .stream()
                          .filter(e -> e.getValue() == NotificationEmailMode.OFF)
                          .map(Map.Entry::getKey)
                          .collect(java.util.stream.Collectors.toSet());
        if (!off.isEmpty())
            cancellation.cancelCategories(id, off);
        return response(modes.current(id));
    }
    private List<NotificationEmailPreferenceResponse> response(
            Map<NotificationCategory, NotificationEmailMode> values) {
        return Arrays.stream(NotificationCategory.values())
                .map(c -> new NotificationEmailPreferenceResponse(c.name(), values.get(c).name()))
                .toList();
    }
    private boolean eligible(UserAccount u) {
        return u.isActive() && u.getMergedToUserId() == null && u.getEmail() != null && !u.getEmail().isBlank()
                && !u.isSystemAccount();
    }
    private UserAccount lockedEligible(String id) {
        return accounts.findLockedFreshById(id)
                .filter(this::eligible)
                .orElseThrow(() -> new DomainBadRequestException("error.notification.email.noEmail"));
    }
    private void requireEnabled() {
        if (!settings.lockCurrent().enabled() || !settings.smtpConfigured())
            throw new DomainBadRequestException("error.notification.email.disabled");
    }
    /** BCrypt has a 72-byte input boundary; hash the full purpose/account/code before slow encoding. */
    private String codeInput(String id, String code) {
        try {
            byte[] input = ("notification-email:" + id + ":" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
    private String mask(String email) {
        if (email == null)
            return null;
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }
}
