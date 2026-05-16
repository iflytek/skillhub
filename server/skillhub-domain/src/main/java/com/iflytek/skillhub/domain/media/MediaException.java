package com.iflytek.skillhub.domain.media;

/**
 * Domain-level error for media uploads (file-header rejection, size limit, etc.).
 * Message is the i18n key (e.g. {@code error.media.gif.invalidSignature}).
 */
public class MediaException extends RuntimeException {
    public MediaException(String messageCode) {
        super(messageCode);
    }
}
