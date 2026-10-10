package com.iflytek.skillhub.domain.security;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps Spring transaction callback plumbing out of domain workflows.
 */
final class TransactionCommitCallbacks {

    private TransactionCommitCallbacks() {
    }

    /**
     * Runs the callback after the current transaction commits, or immediately when no transaction
     * synchronization is active.
     */
    static void afterCommitOrNow(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
    }
}
