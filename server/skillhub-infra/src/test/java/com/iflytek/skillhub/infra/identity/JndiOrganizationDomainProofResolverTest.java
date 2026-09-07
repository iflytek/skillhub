package com.iflytek.skillhub.infra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.organization.OrganizationDomainProofLookupException;
import java.util.concurrent.atomic.AtomicReference;
import javax.naming.CommunicationException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import org.junit.jupiter.api.Test;

class JndiOrganizationDomainProofResolverTest {

    @Test
    void resolvesSingleAndSegmentedTxtValuesWithoutDnsPresentationQuotes() {
        AtomicReference<String> queriedName = new AtomicReference<>();
        BasicAttribute txt = new BasicAttribute("TXT");
        txt.add("\"skillhub-verification=first\"");
        txt.add("\"skillhub-\" \"verification=second\"");
        txt.add("plain=value");
        BasicAttributes attributes = new BasicAttributes();
        attributes.put(txt);
        JndiOrganizationDomainProofResolver resolver =
                new JndiOrganizationDomainProofResolver(recordName -> {
                    queriedName.set(recordName);
                    return attributes;
                });

        assertThat(resolver.resolveTxt("_skillhub-verification.example.com"))
                .containsExactly(
                        "skillhub-verification=first",
                        "skillhub-verification=second",
                        "plain=value"
                );
        assertThat(queriedName).hasValue("_skillhub-verification.example.com");
    }

    @Test
    void dnsFailuresUseAStableRedactedServiceUnavailableError() {
        JndiOrganizationDomainProofResolver resolver =
                new JndiOrganizationDomainProofResolver(recordName -> {
                    throw new CommunicationException("secret-token at " + recordName);
                });

        assertThatThrownBy(() -> resolver.resolveTxt(
                "_skillhub-verification.example.com"
        ))
                .isInstanceOf(OrganizationDomainProofLookupException.class)
                .hasMessage("error.organization.domain.dns-unavailable")
                .hasNoCause();
    }
}
