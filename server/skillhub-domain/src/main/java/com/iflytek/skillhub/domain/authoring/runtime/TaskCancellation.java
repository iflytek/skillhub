package com.iflytek.skillhub.domain.authoring.runtime;

/**
 * Cooperative cancellation signal handed to runtime adapters. Adapters should poll
 * {@link #isCancelRequested()} while executing (at least between output chunks) and
 * abort as quickly as practical when it flips.
 */
@FunctionalInterface
public interface TaskCancellation {

    boolean isCancelRequested();
}
