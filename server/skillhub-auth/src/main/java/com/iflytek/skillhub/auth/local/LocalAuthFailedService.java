package com.iflytek.skillhub.auth.local;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class LocalAuthFailedService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;

    private final LocalCredentialRepository credentialRepository;

    public LocalAuthFailedService(Clock clock,
                                  LocalCredentialRepository credentialRepository
    ){
        this.clock = clock;
        this.credentialRepository = credentialRepository;
    }




    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void handleFailedLogin(@Nonnull Long credentialId) {

        LocalCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid credential id"));

        int failedAttempts = credential.getFailedAttempts() + 1;
        Instant lockedUntil = credential.getLockedUntil();

        if (failedAttempts >= MAX_FAILED_ATTEMPTS && lockedUntil == null) {
            lockedUntil = currentTime().plus(LOCK_DURATION);
        }

        credentialRepository.updateFailedAttemptsAndLockedUntil(credentialId, failedAttempts, lockedUntil);
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }
}
