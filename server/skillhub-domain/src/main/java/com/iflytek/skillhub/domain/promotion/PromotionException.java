package com.iflytek.skillhub.domain.promotion;

/**
 * Domain-level checked-style runtime exception for promotion state machine violations.
 * The {@link #getMessage()} is the i18n message code defined in the design document
 * (e.g. {@code error.promotion.target.notPublic}).
 */
public class PromotionException extends RuntimeException {
    public PromotionException(String messageCode) {
        super(messageCode);
    }
}
