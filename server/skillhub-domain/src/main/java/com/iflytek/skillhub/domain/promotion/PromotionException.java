package com.iflytek.skillhub.domain.promotion;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

/**
 * Domain-level checked-style runtime exception for promotion state machine violations.
 * The {@link #getMessage()} is the i18n message code defined in the design document
 * (e.g. {@code error.promotion.target.notPublic}).
 */
public class PromotionException extends DomainBadRequestException {
    public PromotionException(String messageCode) {
        super(messageCode);
    }
}
