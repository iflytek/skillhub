package com.iflytek.skillhub.auth.local;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import com.iflytek.skillhub.domain.auth.PasswordResetRequestRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Local-account password reset flow backed by one-time email verification
 * codes.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int VERIFICATION_CODE_DIGITS = 6;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetRequestRepository resetRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final LocalCredentialRepository credentialRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public PasswordResetService(PasswordResetRequestRepository resetRequestRepository,
                                UserAccountRepository userAccountRepository,
                                LocalCredentialRepository credentialRepository,
                                PasswordPolicyValidator passwordPolicyValidator,
                                PasswordEncoder passwordEncoder,
                                JavaMailSender mailSender,
                                PasswordResetProperties properties) {
        this.resetRequestRepository = resetRequestRepository;
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * Anonymous/self-service reset request. Always silent on ineligible users to
     * avoid account enumeration.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        Optional<UserAccount> userOpt = findEligibleUserByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for ineligible email");
            return;
        }

        UserAccount user = userOpt.get();
        String code = generateVerificationCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getCodeExpiry());

        invalidatePendingRequests(user.getId(), now);
        resetRequestRepository.save(new PasswordResetRequest(
                user.getId(),
                user.getEmail(),
                passwordEncoder.encode(code),
                expiresAt,
                false,
                null
        ));

        sendVerificationCodeEmail(user.getEmail(), code, false);
    }

    /**
     * Admin-triggered reset request for a specific user.
     */
    @Transactional
    public void adminTriggerPasswordReset(String userId, String adminUserId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.admin.user.notFound", userId));

        if (!isEligibleForReset(user)) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.not.eligible");
        }

        String code = generateVerificationCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getCodeExpiry());

        invalidatePendingRequests(userId, now);
        resetRequestRepository.save(new PasswordResetRequest(
                userId,
                user.getEmail(),
                passwordEncoder.encode(code),
                expiresAt,
                true,
                adminUserId
        ));

        sendVerificationCodeEmail(user.getEmail(), code, true);
    }

    /**
     * Verifies a code and updates the local credential password.
     */
    @Transactional
    public void confirmPasswordReset(String email, String code, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        UserAccount user = findUserByEmail(normalizedEmail)
                .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.invalid.code"));

        List<PasswordResetRequest> pendingRequests = resetRequestRepository
                .findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), Instant.now());

        PasswordResetRequest matchedRequest = pendingRequests.stream()
                .filter(request -> passwordEncoder.matches(code, request.getCodeHash()))
                .findFirst()
                .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.invalid.code"));

        var passwordErrors = passwordPolicyValidator.validate(newPassword);
        if (!passwordErrors.isEmpty()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, passwordErrors.getFirst());
        }

        LocalCredential credential = credentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.no.credential"));

        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);

        Instant now = Instant.now();
        matchedRequest.markConsumed(now);
        resetRequestRepository.save(matchedRequest);
        invalidatePendingRequests(user.getId(), now);
    }

    private void invalidatePendingRequests(String userId, Instant now) {
        List<PasswordResetRequest> pending = resetRequestRepository
                .findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, now);
        for (PasswordResetRequest request : pending) {
            request.markConsumed(now);
            resetRequestRepository.save(request);
        }
    }

    private Optional<UserAccount> findEligibleUserByEmail(String normalizedEmail) {
        return findUserByEmail(normalizedEmail)
                .filter(this::isEligibleForReset);
    }

    private Optional<UserAccount> findUserByEmail(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) {
            return Optional.empty();
        }
        return userAccountRepository.findByEmailIgnoreCase(normalizedEmail);
    }

    private boolean isEligibleForReset(UserAccount user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        if (!StringUtils.hasText(user.getEmail())) {
            return false;
        }
        return credentialRepository.findByUserId(user.getId()).isPresent();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateEmail(String email) {
        if (email == null) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.password.reset.email.notBlank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.password.reset.email.invalid");
        }
    }

    private String generateVerificationCode() {
        int bound = (int) Math.pow(10, VERIFICATION_CODE_DIGITS);
        int code = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + VERIFICATION_CODE_DIGITS + "d", code);
    }

    private void sendVerificationCodeEmail(String email, String code, boolean failOnError) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromAddress());
        message.setTo(email);
        message.setSubject("SkillHub password reset verification code");
        message.setText(buildVerificationCodeBody(code));
        try {
            mailSender.send(message);
            log.info("Password reset verification code sent to {}", email);
        } catch (Exception ex) {
            if (failOnError) {
                log.error("Failed to send password reset verification code to {}", email, ex);
                throw new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR, "error.auth.password.reset.email.failed");
            }
            log.warn("Failed to send password reset verification code to {}", email, ex);
        }
    }

    private String resolveFromAddress() {
        String fromAddress = properties.getEmailFromAddress();
        if (!StringUtils.hasText(properties.getEmailFromName())) {
            return fromAddress;
        }
        return properties.getEmailFromName() + " <" + fromAddress + ">";
    }

    private String buildVerificationCodeBody(String code) {
        long expiryMinutes = Math.max(1L, properties.getCodeExpiry().toMinutes());
        return "Your SkillHub password reset verification code is: " + code
                + "\n\nThis code expires in " + expiryMinutes + " minutes."
                + "\n\nIf you did not request a password reset, please ignore this email.";
    }
}
