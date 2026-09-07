package com.iflytek.skillhub.domain.audit;

import java.util.Objects;

/** Typed, tenant-correlated audit event whose detail cannot carry protocol Secrets. */
public record OrganizationAuditEvent(
        String actorUserId,
        String organizationId,
        OrganizationAuditAction action,
        OrganizationAuditTargetType targetType,
        String targetReference,
        OrganizationAuditResult result,
        String requestId,
        OrganizationAuditDetail detail
) {

    public OrganizationAuditEvent {
        actorUserId = requireNonBlank(actorUserId, "actorUserId");
        organizationId = requireNonBlank(organizationId, "organizationId");
        action = Objects.requireNonNull(action, "action");
        targetType = Objects.requireNonNull(targetType, "targetType");
        targetReference = requireNonBlank(targetReference, "targetReference");
        result = Objects.requireNonNull(result, "result");
        requestId = requireNonBlank(requestId, "requestId");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static OrganizationAuditEvent success(
            String actorUserId,
            String organizationId,
            OrganizationAuditAction action,
            OrganizationAuditTargetType targetType,
            String targetReference,
            String requestId,
            OrganizationAuditDetail detail
    ) {
        return new OrganizationAuditEvent(
                actorUserId,
                organizationId,
                action,
                targetType,
                targetReference,
                OrganizationAuditResult.SUCCESS,
                requestId,
                detail
        );
    }

    @Override
    public String toString() {
        return "OrganizationAuditEvent[actorUserId=%s, organizationId=%s, action=%s, "
                + "targetType=%s, targetReference=%s, result=%s, requestId=%s, detail=<redacted>]"
                .formatted(
                        actorUserId,
                        organizationId,
                        action,
                        targetType,
                        targetReference,
                        result,
                        requestId
                );
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
