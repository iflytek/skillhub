package com.iflytek.skillhub.domain.shared.exception;

/**
 * Shared contract for exceptions that expose a localized message code and
 * interpolation arguments regardless of module boundary.
 */
public interface LocalizedMessage {
    String messageCode();

    Object[] messageArgs();
}
