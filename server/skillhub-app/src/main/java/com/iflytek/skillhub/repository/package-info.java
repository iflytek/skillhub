/**
 * Application-side query repositories for controller-facing read models.
 *
 * <p>This package exists for read assembly that does not belong in domain write
 * workflows: cross-aggregate joins, summary-card projection, inbox row
 * assembly, compatibility mapping, snapshot fallback, and similar
 * presentation-oriented queries.
 *
 * <p>These repositories are intended to be called by application services.
 * They should not absorb domain mutation rules, and they should not replace
 * domain repository ports that are still needed for aggregate persistence and
 * business-state transitions.
 */
package com.iflytek.skillhub.repository;
