package com.iflytek.skillhub.auth.connection.secret;

import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Transactional control plane and privacy-preserving data plane for Login Secrets. */
public class LoginConnectionSecretService implements SecretMaterialResolver {

    public static final Duration MAX_ROTATION_OVERLAP = Duration.ofHours(24);

    private final LoginConnectionRepository connectionRepository;
    private final LoginConnectionSecretRepository secretRepository;
    private final SecretEnvelopeCipher envelopeCipher;

    public LoginConnectionSecretService(
            LoginConnectionRepository connectionRepository,
            LoginConnectionSecretRepository secretRepository,
            SecretEnvelopeCipher envelopeCipher
    ) {
        this.connectionRepository = Objects.requireNonNull(
                connectionRepository,
                "connectionRepository"
        );
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository");
        this.envelopeCipher = Objects.requireNonNull(envelopeCipher, "envelopeCipher");
    }

    @Transactional
    public SecretReference rotate(
            String organizationId,
            String connectionId,
            SecretPurpose purpose,
            SecretMaterial newMaterial,
            Duration overlap,
            String actorId,
            Instant occurredAt
    ) {
        String tenant = requireText(organizationId, "organizationId");
        String connection = requireText(connectionId, "connectionId");
        SecretPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");
        SecretMaterial material = Objects.requireNonNull(newMaterial, "newMaterial");
        Duration rotationOverlap = requireOverlap(overlap);
        String actor = requireText(actorId, "actorId");
        Instant now = Objects.requireNonNull(occurredAt, "occurredAt");

        connectionRepository.lockByOrganizationIdAndId(tenant, connection)
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.loginConnection.notFound"
                ));
        List<LoginConnectionSecretVersion> existing =
                secretRepository.findAllForUpdate(tenant, connection);
        long nextVersion = existing.stream()
                .map(LoginConnectionSecretVersion::getBindingVersion)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;

        existing.stream()
                .filter(secret -> secret.getPurpose().equals(requiredPurpose))
                .filter(secret -> secret.getStatus() == LoginConnectionSecretStatus.RETIRING)
                .forEach(secret -> {
                    secret.revoke(actor, now);
                    secretRepository.saveAndFlush(secret);
                });
        existing.stream()
                .filter(secret -> secret.getPurpose().equals(requiredPurpose))
                .filter(secret -> secret.getStatus() == LoginConnectionSecretStatus.CURRENT)
                .findFirst()
                .ifPresent(secret -> {
                    if (rotationOverlap.isZero()) {
                        secret.revoke(actor, now);
                    } else {
                        secret.retireUntil(now.plus(rotationOverlap), now);
                    }
                    secretRepository.saveAndFlush(secret);
                });

        SecretBindingContext context = new SecretBindingContext(
                tenant,
                connection,
                requiredPurpose,
                nextVersion
        );
        EncryptedSecretEnvelope envelope = envelopeCipher.encrypt(context, material);
        LoginConnectionSecretVersion stored = secretRepository.saveAndFlush(
                LoginConnectionSecretVersion.current(
                        tenant,
                        connection,
                        requiredPurpose,
                        nextVersion,
                        envelope,
                        actor,
                        now
                )
        );
        return stored.reference();
    }

    @Transactional(readOnly = true)
    public SecretConfigurationSummary describe(
            String organizationId,
            String connectionId,
            SecretPurpose purpose
    ) {
        List<LoginConnectionSecretVersion> versions = secretRepository.findAll(
                requireText(organizationId, "organizationId"),
                requireText(connectionId, "connectionId"),
                Objects.requireNonNull(purpose, "purpose")
        );
        LoginConnectionSecretVersion current = versions.stream()
                .filter(secret -> secret.getStatus() == LoginConnectionSecretStatus.CURRENT)
                .findFirst()
                .orElse(null);
        if (current == null) {
            return SecretConfigurationSummary.notConfigured();
        }
        Instant previousValidUntil = versions.stream()
                .filter(secret -> secret.getStatus() == LoginConnectionSecretStatus.RETIRING)
                .map(LoginConnectionSecretVersion::getValidUntil)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return SecretConfigurationSummary.configured(current.getUpdatedAt(), previousValidUntil);
    }

    @Transactional
    public void revoke(
            String organizationId,
            String connectionId,
            SecretPurpose purpose,
            long bindingVersion,
            String actorId,
            Instant occurredAt
    ) {
        String tenant = requireText(organizationId, "organizationId");
        String connection = requireText(connectionId, "connectionId");
        SecretPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");
        connectionRepository.lockByOrganizationIdAndId(tenant, connection)
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.loginConnection.notFound"
                ));
        LoginConnectionSecretVersion secret = secretRepository.findAllForUpdate(tenant, connection)
                .stream()
                .filter(candidate -> candidate.getPurpose().equals(requiredPurpose))
                .filter(candidate -> candidate.getBindingVersion() == bindingVersion)
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.loginConnection.secret.notFound"
                ));
        secret.revoke(
                requireText(actorId, "actorId"),
                Objects.requireNonNull(occurredAt, "occurredAt")
        );
        secretRepository.saveAndFlush(secret);
    }

    @Override
    @Transactional(readOnly = true)
    public SecretMaterial resolve(
            SecretReference reference,
            SecretPurpose expectedPurpose,
            Instant now
    ) {
        SecretReference requiredReference = Objects.requireNonNull(reference, "reference");
        if (!requiredReference.purpose().equals(
                Objects.requireNonNull(expectedPurpose, "expectedPurpose")
        )) {
            throw new SecretMaterialUnavailableException();
        }
        LoginConnectionSecretVersion secret = secretRepository.findByReference(requiredReference)
                .filter(candidate -> candidate.isResolvableAt(
                        Objects.requireNonNull(now, "now")
                ))
                .orElseThrow(SecretMaterialUnavailableException::new);
        try {
            return envelopeCipher.decrypt(requiredReference.context(), secret.envelope());
        } catch (SecretMaterialUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretMaterialUnavailableException();
        }
    }

    private static Duration requireOverlap(Duration overlap) {
        Duration required = Objects.requireNonNull(overlap, "overlap");
        if (required.isNegative() || required.compareTo(MAX_ROTATION_OVERLAP) > 0) {
            throw new DomainBadRequestException(
                    "error.loginConnection.secret.rotation.overlap.invalid"
            );
        }
        return required;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
