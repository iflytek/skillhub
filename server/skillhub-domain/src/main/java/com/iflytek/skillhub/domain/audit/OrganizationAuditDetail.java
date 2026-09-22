package com.iflytek.skillhub.domain.audit;

/**
 * Allowlisted Organization audit detail.
 *
 * <p>Protocol payloads, domain proof values, tokens, credentials, email addresses and
 * display names intentionally have no field in this type.</p>
 */
public record OrganizationAuditDetail(
        String stateFrom,
        String stateTo,
        String role,
        String sourceType,
        String subjectReference
) {

    public static OrganizationAuditDetail transition(String stateFrom, String stateTo) {
        return new OrganizationAuditDetail(stateFrom, stateTo, null, null, null);
    }

    public static OrganizationAuditDetail member(
            String stateTo,
            String sourceType,
            String subjectReference
    ) {
        return new OrganizationAuditDetail(
                null,
                stateTo,
                null,
                sourceType,
                subjectReference
        );
    }

    public static OrganizationAuditDetail role(
            String stateTo,
            String role,
            String subjectReference
    ) {
        return new OrganizationAuditDetail(null, stateTo, role, null, subjectReference);
    }

    public String toJson() {
        return AuditDetail.builder()
                .put("stateFrom", stateFrom)
                .put("stateTo", stateTo)
                .put("role", role)
                .put("sourceType", sourceType)
                .put("subjectReference", subjectReference)
                .build();
    }

    public OrganizationAuditDetail {
        if (stateFrom == null
                && stateTo == null
                && role == null
                && sourceType == null
                && subjectReference == null) {
            throw new IllegalArgumentException("Organization audit detail cannot be empty");
        }
        if (subjectReference != null && subjectReference.isBlank()) {
            throw new IllegalArgumentException("subjectReference cannot be blank");
        }
    }
}
