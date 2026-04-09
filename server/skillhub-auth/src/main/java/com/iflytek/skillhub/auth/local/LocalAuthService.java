package com.iflytek.skillhub.auth.local;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles username-and-password registration and login for first-party local
 * accounts.
 */
@Service
public class LocalAuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,64}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    // Precomputed BCrypt hash for "skillhub-local-auth-dummy". Used to blur timing
    // differences between existing and non-existing usernames during login.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$8Q/2o2A0V.b18G2DutV4c.s5zZxH6MECM7tP8mYv6b6Q6x6o9v3vu";

    private final LocalCredentialRepository credentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public LocalAuthService(LocalCredentialRepository credentialRepository,
                            UserAccountRepository userAccountRepository,
                            UserRoleBindingRepository userRoleBindingRepository,
                            GlobalNamespaceMembershipService globalNamespaceMembershipService,
                            PasswordPolicyValidator passwordPolicyValidator,
                            PasswordEncoder passwordEncoder,
                            Clock clock) {
        this.credentialRepository = credentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Registers a new local user, creates the credential record, and ensures
     * the user is enrolled in the global namespace.
     */
    @Transactional
    public PlatformPrincipal register(String username, String password, String email) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername);

        if (credentialRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.local.username.exists");
        }

        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
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

    /**
     * Authenticates a local account and returns the principal snapshot used to
     * establish a web session.
     */
    @Transactional
    public PlatformPrincipal login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        LocalCredential credential = credentialRepository.findByUsernameIgnoreCase(normalizedUsername)
            .orElse(null);

        if (credential == null) {
            passwordEncoder.matches(password == null ? "" : password, DUMMY_PASSWORD_HASH);
            throw invalidCredentials();
        }

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

    /**
     * Changes the stored password for an already authenticated local account.
     */
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
        roles = PlatformRoleDefaults.withDefaultUserRole(roles);
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
        Instant now = currentTime();
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, credential.getLockedUntil()).toMinutes());
            throw new AuthFlowException(HttpStatus.LOCKED, "error.auth.local.locked", minutes);
        }
    }

    private void handleFailedLogin(LocalCredential credential) {
        int failedAttempts = credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            credential.setLockedUntil(currentTime().plus(LOCK_DURATION));
        }
        credentialRepository.save(credential);
    }

    private Instant currentTime() {
        return Instant.now(clock);
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

    private void validateEmail(String email) {
        if (email == null) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.local.email.notBlank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.local.email.invalid");
        }
    }
}
