package com.iflytek.skillhub.domain.suite.bundle;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteBundleExecutionOperationAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

    @Test
    void unknownSourceIsBlockedAgainAfterRetryWithoutInventingAnAuthorizationSnapshot() {
        var operation = operation();
        assertThat(operation.blockIfAuthorizationUnknown(NOW)).isTrue();
        assertThat(operation.getStatus()).isEqualTo(SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE);
        assertThat(operation.getFailureCode()).isEqualTo("AUTHORIZATION_PROVENANCE_UNKNOWN");
        assertThat(operation.getFailureDetail()).isEqualTo("error.suite.bundle.authorization.provenanceUnknown");
        assertThat(operation.getResultSuiteId()).isNull();
        operation.retry(NOW.plusSeconds(1));
        assertThat(operation.blockIfAuthorizationUnknown(NOW.plusSeconds(2))).isTrue();
        assertThat(operation.getInitiatorType()).isNull();
        assertThat(operation.getTokenCapabilities()).isNull();
    }

    @Test
    void aRecordedSessionIsAllowedWithoutTokenCapabilities() {
        var operation = operation();
        operation.captureAuthorization(false, null);
        assertThat(operation.blockIfAuthorizationUnknown(NOW)).isFalse();
        assertThat(operation.getStatus()).isEqualTo(SkillSuiteBundleOperationStatus.RUNNING);
    }

    @Test
    void knownTokenDoesNotNeedPrivatePublishForReferenceRecoveryButStillCannotConfirmPrivatePublish() {
        var operation = operation();
        var capabilities = Set.of("skill:read", "skill:submit", "suite:legacy-write");
        operation.captureAuthorization(true, capabilities);
        assertThat(operation.blockIfAuthorizationUnknown(NOW)).isFalse();
        assertThat(operation.privatePublishBlockCode()).isEqualTo("TOKEN_CAPABILITY_MISSING");
        assertThat(operation.getTokenCapabilities()).isEqualTo(capabilities);
    }

    @Test
    void corruptedOrMissingPersistedTokenSnapshotsAreNotTreatedAsSessionAuthority() throws Exception {
        for (Set<String> capabilities : Arrays.<Set<String>>asList(null, Set.of())) {
            var operation = operation();
            setPersistedField(operation, "initiatorType", "TOKEN");
            setPersistedField(operation, "tokenCapabilities", capabilities);
            assertThat(operation.blockIfAuthorizationUnknown(NOW)).isTrue();
            assertThat(operation.getFailureCode()).isEqualTo("AUTHORIZATION_PROVENANCE_UNKNOWN");
        }
    }

    @Test
    void unrecognizedPersistedInitiatorCannotBorrowWorkerAuthority() throws Exception {
        var operation = operation();
        setPersistedField(operation, "initiatorType", "WORKER");
        setPersistedField(operation, "tokenCapabilities", Set.of("skill:publish-private"));
        assertThat(operation.blockIfAuthorizationUnknown(NOW)).isTrue();
        assertThat(operation.privatePublishBlockCode()).isEqualTo("AUTHORIZATION_PROVENANCE_UNKNOWN");
    }

    private void setPersistedField(SkillSuiteBundleExecutionOperation operation, String name, Object value)
            throws ReflectiveOperationException {
        var field = SkillSuiteBundleExecutionOperation.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(operation, value);
    }

    private SkillSuiteBundleExecutionOperation operation() {
        return new SkillSuiteBundleExecutionOperation(
                "operation", "preview", "request", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive", "a".repeat(64), Map.of(), "digest", NOW);
    }
}
