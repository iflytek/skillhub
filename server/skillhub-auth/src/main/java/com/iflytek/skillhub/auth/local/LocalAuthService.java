package com.iflytek.skillhub.auth.local;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalAuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,64}$");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final LocalCredentialRepository credentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    public LocalAuthService(LocalCredentialRepository credentialRepository,
                            UserAccountRepository userAccountRepository,
                            UserRoleBindingRepository userRoleBindingRepository,
                            GlobalNamespaceMembershipService globalNamespaceMembershipService,
                            PasswordPolicyValidator passwordPolicyValidator,
                            PasswordEncoder passwordEncoder) {
        this.credentialRepository = credentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public PlatformPrincipal register(String username, String password, String email) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername);

        if (credentialRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.local.username.exists");
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && userAccountRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.local.email.exists");
        }

        var passwordErrors = passwordPolicyValidator.validate(password);
        if (!passwordErrors.isEmpty()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, passwordErrors.getFirst());
        }

        UserAccount user = new UserAccount(
            "usr_" + UUID.randomUUID(),
            normalizedUsername,
            normalizedEmail,
            null
        );
        user.setStatus(UserStatus.ACTIVE);
        userAccountRepository.save(user);

        credentialRepository.save(new LocalCredential(
            user.getId(),
            normalizedUsername,
            passwordEncoder.encode(password)
        ));
        globalNamespaceMembershipService.ensureMember(user.getId());

        return buildPrincipal(user);
    }

    @Transactional
    public PlatformPrincipal login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        LocalCredential credential = credentialRepository.findByUsernameIgnoreCase(normalizedUsername)
            .orElseThrow(() -> invalidCredentials());

        UserAccount user = userAccountRepository.findById(credential.getUserId())
            .orElseThrow(() -> new IllegalStateException("User not found for local credential"));

        ensureUserCanLogin(user);
        ensureNotLocked(credential);

        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            handleFailedLogin(credential);
            throw invalidCredentials();
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);
        return buildPrincipal(user);
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        LocalCredential credential = credentialRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.local.notEnabled"));

        if (!passwordEncoder.matches(currentPassword, credential.getPasswordHash())) {
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.local.invalidCredentials");
        }

        var passwordErrors = passwordPolicyValidator.validate(newPassword);
        if (!passwordErrors.isEmpty()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, passwordErrors.getFirst());
        }

        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);
    }

    private PlatformPrincipal buildPrincipal(UserAccount user) {
        Set<String> roles = userRoleBindingRepository.findByUserId(user.getId()).stream()
            .map(binding -> binding.getRole().getCode())
            .collect(Collectors.toSet());
        return new PlatformPrincipal(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getAvatarUrl(),
            "local",
            roles
        );
    }

    private void ensureUserCanLogin(UserAccount user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountDisabled");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountPending");
        }
        if (user.getStatus() == UserStatus.MERGED) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountMerged");
        }
    }

    private void ensureNotLocked(LocalCredential credential) {
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = Math.max(1, Duration.between(LocalDateTime.now(), credential.getLockedUntil()).toMinutes());
            throw new AuthFlowException(HttpStatus.LOCKED, "error.auth.local.locked", minutes);
        }
    }

    private void handleFailedLogin(LocalCredential credential) {
        int failedAttempts = credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            credential.setLockedUntil(LocalDateTime.now().plus(LOCK_DURATION));
        }
        credentialRepository.save(credential);
    }

    private AuthFlowException invalidCredentials() {
        return new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.local.invalidCredentials");
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.local.username.invalid");
        }
    }
}
