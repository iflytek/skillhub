package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ApiTokenLifecyclePersistenceTest {
    @Autowired ApiTokenService service;
    @Autowired ApiTokenRepository tokens;
    @Autowired TransactionTemplate transactions;
    private ApiToken snapshot;

    @BeforeEach
    void loadSnapshotInSeparateTransaction() {
        String owner = "token-lifecycle-" + UUID.randomUUID();
        ApiToken token = service.createToken(owner, "fixture", "[\"skill:read\"]").entity();
        snapshot = tokens.findById(token.getId()).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        if (snapshot != null) tokens.deleteById(snapshot.getId());
    }

    @Test
    void expiryChangeCannotUndoAnInterveningRevocation() {
        TransactionTemplate peer = new TransactionTemplate(transactions.getTransactionManager());
        peer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            // Model the expiration writer's read, then let the separate revocation commit first.
            ApiToken existing = tokens.findById(snapshot.getId()).orElseThrow();
            assertThat(existing.getRevokedAt()).isNull();
            peer.executeWithoutResult(ignored -> service.revokeToken(existing.getId(), existing.getUserId()));
            service.updateExpiration(existing.getId(), existing.getUserId(),
                    Instant.now().plus(1, ChronoUnit.DAYS).toString());
        })).isInstanceOf(DomainNotFoundException.class);
        assertThat(tokens.findById(snapshot.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void recordingUsagePreservesCommittedRevocation() {
        service.revokeToken(snapshot.getId(), snapshot.getUserId());
        Instant revoked = tokens.findById(snapshot.getId()).orElseThrow().getRevokedAt();
        assertThat(revoked).isNotNull();

        service.touchLastUsed(snapshot);
        ApiToken stored = tokens.findById(snapshot.getId()).orElseThrow();
        assertThat(stored.getRevokedAt()).isEqualTo(revoked);
    }

    @Test
    void recordingUsagePreservesCommittedExpiration() {
        Instant expiration = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        service.updateExpiration(snapshot.getId(), snapshot.getUserId(), expiration.toString());
        Instant committed = tokens.findById(snapshot.getId()).orElseThrow().getExpiresAt();
        assertThat(committed).isNotNull();

        service.touchLastUsed(snapshot);
        ApiToken stored = tokens.findById(snapshot.getId()).orElseThrow();
        assertThat(stored.getExpiresAt()).isEqualTo(committed);
        assertThat(stored.getLastUsedAt()).isNotNull();
    }
}
