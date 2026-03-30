package com.iflytek.skillhub.auth.local;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;


@Service
public class LocalAuthFailedService {

    private static final Logger log = LoggerFactory.getLogger(LocalAuthFailedService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Resource
    private LocalCredentialRepository credentialRepository;

    @Resource
    private Clock clock;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailedLogin(LocalCredential credential) {
        int failedAttempts = credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            credential.setLockedUntil(currentTime().plus(LOCK_DURATION));
        }
        credentialRepository.saveAndFlush(credential);
        log.warn("Failed login attempt for user: {}", credential.getUsername());
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }
}
