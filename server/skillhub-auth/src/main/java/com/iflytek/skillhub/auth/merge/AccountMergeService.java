package com.iflytek.skillhub.auth.merge;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates account merge requests and consolidates credentials, bindings,
 * roles, memberships, and tokens into a single primary user.
 */
@Service
public class AccountMergeService {

    private static final Comparator<NamespaceRole> NAMESPACE_ROLE_ORDER = Comparator.comparingInt(role -> switch (role) {
        case MEMBER -> 0;
        case ADMIN -> 1;
        case OWNER -> 2;
    });

    private final AccountMergeRequestRepository mergeRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final IdentityBindingRepository identityBindingRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountMergeService(AccountMergeRequestRepository mergeRequestRepository,
                               UserAccountRepository userAccountRepository,
                               LocalCredentialRepository localCredentialRepository,
                               IdentityBindingRepository identityBindingRepository,
                               UserRoleBindingRepository userRoleBindingRepository,
                               ApiTokenRepository apiTokenRepository,
                               NamespaceMemberRepository namespaceMemberRepository,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.mergeRequestRepository = mergeRequestRepository;
        this.userAccountRepository = userAccountRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.identityBindingRepository = identityBindingRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.apiTokenRepository = apiTokenRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record InitiationResult(Long mergeRequestId, String secondaryUserId, String verificationToken, Instant expiresAt) {}

    @Transactional
    public InitiationResult initiate(String primaryUserId, String secondaryIdentifier) {
        UserAccount primaryUser = loadActiveUser(primaryUserId);
        UserAccount secondaryUser = resolveSecondaryUser(secondaryIdentifier);
        validateMergePair(primaryUser, secondaryUser);

        if (mergeRequestRepository.existsBySecondaryUserIdAndStatus(
            secondaryUser.getId(),
            AccountMergeRequest.STATUS_PENDING
        )) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.merge.pendingExists");
        }

        Optional<LocalCredential> primaryCredential = localCredentialRepository.findByUserId(primaryUserId);
        Optional<LocalCredential> secondaryCredential = localCredentialRepository.findByUserId(secondaryUser.getId());
        if (primaryCredential.isPresent() && secondaryCredential.isPresent()) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.merge.localCredentialConflict");
        }

        String rawToken = generateVerificationToken();
        AccountMergeRequest request = new AccountMergeRequest(
            primaryUserId,
            secondaryUser.getId(),
            passwordEncoder.encode(rawToken),
            currentTime().plus(Duration.ofMinutes(30))
        );
        request = mergeRequestRepository.save(request);
        return new InitiationResult(request.getId(), secondaryUser.getId(), rawToken, request.getTokenExpiresAt());
    }

    @Transactional
    public void verify(String primaryUserId, Long mergeRequestId, String verificationToken) {
        AccountMergeRequest request = mergeRequestRepository.findByIdAndPrimaryUserId(mergeRequestId, primaryUserId)
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.requestNotFound"));
        if (!AccountMergeRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.requestNotPending");
        }
        if (request.getTokenExpiresAt() == null || request.getTokenExpiresAt().isBefore(currentTime())) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.tokenExpired");
        }
        if (!passwordEncoder.matches(verificationToken, request.getVerificationToken())) {
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.merge.invalidToken");
        }

        loadActiveUser(primaryUserId);
        UserAccount secondaryUser = userAccountRepository.findById(request.getSecondaryUserId())
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
        validateMergePair(loadActiveUser(primaryUserId), secondaryUser);

        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        mergeRequestRepository.save(request);
    }

    @Transactional
    public void confirm(String primaryUserId, Long mergeRequestId) {
        AccountMergeRequest request = mergeRequestRepository.findByIdAndPrimaryUserId(mergeRequestId, primaryUserId)
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.requestNotFound"));
        if (!AccountMergeRequest.STATUS_VERIFIED.equals(request.getStatus())) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.requestNotVerified");
        }

        UserAccount primaryUser = loadActiveUser(primaryUserId);
        UserAccount secondaryUser = userAccountRepository.findById(request.getSecondaryUserId())
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
        validateMergePair(primaryUser, secondaryUser);

        migrateIdentityBindings(primaryUser.getId(), secondaryUser.getId());
        migrateApiTokens(primaryUser.getId(), secondaryUser.getId());
        migrateUserRoles(primaryUser.getId(), secondaryUser.getId());
        migrateNamespaceMemberships(primaryUser.getId(), secondaryUser.getId());
        migrateLocalCredential(primaryUser.getId(), secondaryUser.getId());

        if ((primaryUser.getEmail() == null || primaryUser.getEmail().isBlank())
            && secondaryUser.getEmail() != null && !secondaryUser.getEmail().isBlank()) {
            primaryUser.setEmail(secondaryUser.getEmail());
        }
        userAccountRepository.save(primaryUser);

        secondaryUser.setStatus(UserStatus.MERGED);
        secondaryUser.setMergedToUserId(primaryUser.getId());
        userAccountRepository.save(secondaryUser);

        request.setStatus(AccountMergeRequest.STATUS_COMPLETED);
        request.setCompletedAt(currentTime());
        request.setVerificationToken(null);
        mergeRequestRepository.save(request);
    }

    private UserAccount resolveSecondaryUser(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim();
        if (normalized.isBlank()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.identifierRequired");
        }
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.identifierInvalid");
            }
            IdentityBinding binding = identityBindingRepository.findByProviderCodeAndSubject(parts[0], parts[1])
                .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
            return userAccountRepository.findById(binding.getUserId())
                .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
        }

        LocalCredential credential = localCredentialRepository.findByUsernameIgnoreCase(normalized.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
        return userAccountRepository.findById(credential.getUserId())
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.secondaryNotFound"));
    }

    private UserAccount loadActiveUser(String userId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthFlowException(HttpStatus.NOT_FOUND, "error.auth.merge.primaryNotFound"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.primaryNotActive");
        }
        return user;
    }

    private void validateMergePair(UserAccount primaryUser, UserAccount secondaryUser) {
        if (primaryUser.getId().equals(secondaryUser.getId())) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.sameAccount");
        }
        if (secondaryUser.getStatus() != UserStatus.ACTIVE) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.merge.secondaryNotActive");
        }
    }

    private void migrateIdentityBindings(String primaryUserId, String secondaryUserId) {
        List<IdentityBinding> bindings = identityBindingRepository.findByUserId(secondaryUserId);
        for (IdentityBinding binding : bindings) {
            binding.setUserId(primaryUserId);
        }
        identityBindingRepository.saveAll(bindings);
    }

    private void migrateApiTokens(String primaryUserId, String secondaryUserId) {
        List<ApiToken> tokens = apiTokenRepository.findByUserId(secondaryUserId);
        for (ApiToken token : tokens) {
            token.setUserId(primaryUserId);
            if ("USER".equals(token.getSubjectType())) {
                token.setSubjectId(primaryUserId);
            }
        }
        apiTokenRepository.saveAll(tokens);
    }

    private void migrateUserRoles(String primaryUserId, String secondaryUserId) {
        Set<String> primaryRoleCodes = new HashSet<>();
        for (UserRoleBinding binding : userRoleBindingRepository.findByUserId(primaryUserId)) {
            primaryRoleCodes.add(binding.getRole().getCode());
        }

        List<UserRoleBinding> secondaryBindings = userRoleBindingRepository.findByUserId(secondaryUserId);
        for (UserRoleBinding binding : secondaryBindings) {
            Role role = binding.getRole();
            if (!primaryRoleCodes.contains(role.getCode())) {
                userRoleBindingRepository.save(new UserRoleBinding(primaryUserId, role));
                primaryRoleCodes.add(role.getCode());
            }
        }
        userRoleBindingRepository.deleteAll(secondaryBindings);
    }

    private void migrateNamespaceMemberships(String primaryUserId, String secondaryUserId) {
        List<NamespaceMember> secondaryMemberships = namespaceMemberRepository.findByUserId(secondaryUserId);
        for (NamespaceMember secondaryMembership : secondaryMemberships) {
            Optional<NamespaceMember> existingPrimaryMembership = namespaceMemberRepository
                .findByNamespaceIdAndUserId(secondaryMembership.getNamespaceId(), primaryUserId);
            if (existingPrimaryMembership.isPresent()) {
                NamespaceMember primaryMembership = existingPrimaryMembership.get();
                if (NAMESPACE_ROLE_ORDER.compare(secondaryMembership.getRole(), primaryMembership.getRole()) > 0) {
                    primaryMembership.setRole(secondaryMembership.getRole());
                    namespaceMemberRepository.save(primaryMembership);
                }
                namespaceMemberRepository.deleteByNamespaceIdAndUserId(
                    secondaryMembership.getNamespaceId(),
                    secondaryUserId
                );
            } else {
                secondaryMembership.setUserId(primaryUserId);
                namespaceMemberRepository.save(secondaryMembership);
            }
        }
    }

    private void migrateLocalCredential(String primaryUserId, String secondaryUserId) {
        Optional<LocalCredential> primaryCredential = localCredentialRepository.findByUserId(primaryUserId);
        Optional<LocalCredential> secondaryCredential = localCredentialRepository.findByUserId(secondaryUserId);
        if (primaryCredential.isPresent() && secondaryCredential.isPresent()) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.merge.localCredentialConflict");
        }
        secondaryCredential.ifPresent(credential -> {
            credential.setUserId(primaryUserId);
            localCredentialRepository.save(credential);
        });
    }

    private String generateVerificationToken() {
        byte[] tokenBytes = new byte[24];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }
}
