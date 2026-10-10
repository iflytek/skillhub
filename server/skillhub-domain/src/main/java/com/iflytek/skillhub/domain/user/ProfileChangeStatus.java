package com.iflytek.skillhub.domain.user;

/**
 * Status of a profile change request throughout its lifecycle.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING ──→ APPROVED   (human reviewer approves)
 *   PENDING ──→ REJECTED   (human reviewer rejects)
 *   PENDING ──→ CANCELLED  (user submits a new request, replacing this one)
 *   (direct) ──→ MACHINE_REJECTED  (machine review rejects before entering queue)
 * </pre>
 */
public enum ProfileChangeStatus {
    /** Awaiting human review. */
    PENDING,
    /** Rejected by machine review (e.g. sensitive word detection). */
    MACHINE_REJECTED,
    /** Approved and applied to user_account. */
    APPROVED,
    /** Rejected by human reviewer. */
    REJECTED,
    /** Superseded by a newer request from the same user. */
    CANCELLED
}
