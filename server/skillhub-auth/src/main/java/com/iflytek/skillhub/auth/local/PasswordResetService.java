package com.iflytek.skillhub.auth.local;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import com.iflytek.skillhub.domain.auth.PasswordResetRequestRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetRequestRepository resetRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final LocalCredentialRepository credentialRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    @Value("${skillhub.public.base-url}")
    private String publicBaseUrl;

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

    @Transactional
    public void requestPasswordReset(String identifier) {
        Optional<UserAccount> userOpt = findEligibleUser(identifier);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for ineligible identifier");
            return;
        }

        UserAccount user = userOpt.get();
        String token = generateToken();
        String tokenHash = passwordEncoder.encode(token);
        Instant expiresAt = Instant.now().plus(properties.getTokenExpiry());

        resetRequestRepository.invalidatePendingRequests(user.getId());
        PasswordResetRequest request = new PasswordResetRequest(
            user.getId(), user.getEmail(), tokenHash, expiresAt, false, null
        );
        resetRequestRepository.save(request);
        sendResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void adminTriggerPasswordReset(String userId, String adminUserId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.user.not.found"));

        if (!isEligibleForReset(user)) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.not.eligible");
        }

        String token = generateToken();
        String tokenHash = passwordEncoder.encode(token);
        Instant expiresAt = Instant.now().plus(properties.getTokenExpiry());

        resetRequestRepository.invalidatePendingRequests(userId);
        PasswordResetRequest request = new PasswordResetRequest(
            userId, user.getEmail(), tokenHash, expiresAt, true, adminUserId
        );
        resetRequestRepository.save(request);
        sendResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        PasswordResetRequest request = findValidResetRequest(token);
        passwordPolicyValidator.validate(newPassword);

        LocalCredential credential = credentialRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.no.credential"));

        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);

        request.markConsumed();
        resetRequestRepository.save(request);
        resetRequestRepository.invalidatePendingRequests(request.getUserId());
    }

    private PasswordResetRequest findValidResetRequest(String token) {
        return resetRequestRepository.findAllPendingNotExpired(Instant.now())
            .stream()
            .filter(r -> passwordEncoder.matches(token, r.getTokenHash()))
            .findFirst()
            .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.invalid.token"));
    }

    private Optional<UserAccount> findEligibleUser(String identifier) {
        Optional<UserAccount> userOpt = identifier.contains("@")
            ? userAccountRepository.findByEmailIgnoreCase(identifier)
            : credentialRepository.findByUsernameIgnoreCase(identifier)
                .flatMap(c -> userAccountRepository.findById(c.getUserId()));
        return userOpt.filter(this::isEligibleForReset);
    }

    private boolean isEligibleForReset(UserAccount user) {
        if (user.getStatus() != UserStatus.ACTIVE) return false;
        if (user.getEmail() == null || user.getEmail().isBlank()) return false;
        return credentialRepository.findByUserId(user.getId()).isPresent();
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private void sendResetEmail(String email, String token) {
        String resetUrl = publicBaseUrl + "/reset-password?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmailFromName() + " <" + properties.getEmailFromAddress() + ">");
        message.setTo(email);
        message.setSubject("Password Reset Request");
        message.setText("Click the link to reset your password:\n\n" + resetUrl + "\n\nThis link expires in 30 minutes.");
        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email", e);
            throw new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR, "error.auth.password.reset.email.failed");
        }
    }
}
