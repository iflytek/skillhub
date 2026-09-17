package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Emits a stable security-event shape without protocol claims, email, token or session data. */
@Component
public class Slf4jEnterpriseIdentitySecurityEventRecorder
        implements EnterpriseIdentitySecurityEventRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            Slf4jEnterpriseIdentitySecurityEventRecorder.class
    );

    @Override
    public void record(
            EnterpriseIdentitySecurityEventType type,
            String organizationId,
            String connectionId,
            String targetReference
    ) {
        LOGGER.warn(
                "enterprise_identity_security_event type={} organizationId={} connectionId={} "
                        + "targetReference={} requestId={}",
                Objects.requireNonNull(type, "type"),
                requireOpaqueReference(organizationId, "organizationId"),
                requireOpaqueReference(connectionId, "connectionId"),
                requireOpaqueReference(targetReference, "targetReference"),
                requestId()
        );
    }

    private static String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? "unavailable" : requestId;
    }

    private static String requireOpaqueReference(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > 128
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
