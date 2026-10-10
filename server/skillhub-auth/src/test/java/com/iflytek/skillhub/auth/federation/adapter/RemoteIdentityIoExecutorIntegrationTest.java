package com.iflytek.skillhub.auth.federation.adapter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteIdentityIoExecutorIntegrationTest {

    @Test
    void execute_suspendsAmbientTransactionForSimulatedMetadataTokenAndUserinfoIo() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        RemoteIdentityIoExecutor executor = new TransactionSuspendingRemoteIdentityIoExecutor(
                transactionManager
        );
        TransactionTemplate ambientTransaction = new TransactionTemplate(transactionManager);
        AtomicInteger completedRemoteCalls = new AtomicInteger();

        ambientTransaction.executeWithoutResult(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

            for (String phase : List.of("metadata", "token", "userinfo")) {
                String result = executor.execute(() -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("%s I/O must run outside a database transaction", phase)
                            .isFalse();
                    completedRemoteCalls.incrementAndGet();
                    return phase;
                });
                assertThat(result).isEqualTo(phase);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("the caller transaction must be restored after %s I/O", phase)
                        .isTrue();
            }
        });

        assertThat(completedRemoteCalls).hasValue(3);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<TransactionObject> current = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            TransactionObject transaction = current.get();
            return transaction == null ? new TransactionObject() : transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionObject) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionObject transactionObject = (TransactionObject) transaction;
            transactionObject.active = true;
            current.set(transactionObject);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            current.remove();
            return transaction;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            current.set((TransactionObject) suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // The test manager only models Spring's thread-bound transaction lifecycle.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // The test manager only models Spring's thread-bound transaction lifecycle.
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TransactionObject) transaction).active = false;
            current.remove();
        }
    }

    private static final class TransactionObject {
        private boolean active;
    }
}
