package com.iflytek.skillhub.auth.federation.adapter;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring transaction boundary for metadata, token and userinfo network calls. */
@Component
public final class TransactionSuspendingRemoteIdentityIoExecutor implements RemoteIdentityIoExecutor {

    private final TransactionTemplate transactions;

    public TransactionSuspendingRemoteIdentityIoExecutor(
            PlatformTransactionManager transactionManager
    ) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public <T> T execute(Supplier<T> remoteIo) {
        Objects.requireNonNull(remoteIo, "remoteIo");
        return transactions.execute(ignored -> remoteIo.get());
    }
}
