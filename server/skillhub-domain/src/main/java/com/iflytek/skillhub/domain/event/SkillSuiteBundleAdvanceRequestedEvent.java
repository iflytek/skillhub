package com.iflytek.skillhub.domain.event;

/** Requests asynchronous progress for one durable Suite Bundle operation. */
public record SkillSuiteBundleAdvanceRequestedEvent(String operationId) {
}
