package com.iflytek.skillhub.auth.federation.adapter;

import java.util.function.Supplier;

/** Executes calls to remote identity systems without holding a database transaction open. */
@FunctionalInterface
public interface RemoteIdentityIoExecutor {

    <T> T execute(Supplier<T> remoteIo);
}
