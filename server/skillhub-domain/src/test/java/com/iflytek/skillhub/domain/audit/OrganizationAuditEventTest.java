package com.iflytek.skillhub.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OrganizationAuditEventTest {

    @Test
    void serializedEventContainsCorrelationFieldsAndOnlyAllowlistedDetail() throws Exception {
        OrganizationAuditEvent event = OrganizationAuditEvent.success(
                "actor-1",
                "organization-1",
                OrganizationAuditAction.DOMAIN_CHALLENGE_ISSUED,
                OrganizationAuditTargetType.DOMAIN,
                "domain-1",
                "request-1",
                OrganizationAuditDetail.transition(null, "PENDING")
        );

        String serialized = new ObjectMapper().writeValueAsString(event);

        assertThat(serialized)
                .contains(
                        "\"actorUserId\":\"actor-1\"",
                        "\"organizationId\":\"organization-1\"",
                        "\"result\":\"SUCCESS\"",
                        "\"requestId\":\"request-1\"",
                        "\"stateTo\":\"PENDING\""
                )
                .doesNotContainIgnoringCase(
                        "token",
                        "proof",
                        "secret",
                        "credential",
                        "password",
                        "assertion"
                );
        assertThat(event.toString()).doesNotContain("stateTo", "PENDING");
    }

    @Test
    void eventContractHasNoSensitivePayloadField() {
        assertThat(Arrays.stream(OrganizationAuditEvent.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.matches(
                        "(?i).*(token|proof|secret|credential|password|assertion|payload).*"
                ));
    }
}
