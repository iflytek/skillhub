package com.iflytek.skillhub.auth.sso;

import java.time.Duration;
import java.util.Map;

import com.iflytek.skillhub.auth.config.SsoProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client that validates a CAS service-ticket against the SSO server and
 * returns the associated user identity.
 */
@Service
public class SsoClient {

    private final SsoProperties properties;
    private final RestTemplate restTemplate;

    public SsoClient(SsoProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Validates a CAS service ticket and returns the resolved user identity,
     * or throws {@link TicketValidationException} when the ticket is invalid
     * or the SSO server is unreachable.
     */
    public SsoUser validateTicket(String ticket) {
        var request = Map.of(
                "Ticket", ticket,
                "Url", properties.getClientUrl(),
                "Token", properties.getClientToken()
        );
        var validateUrl = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path(properties.getValidatePath())
                .build()
                .toUriString();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(validateUrl, request, Map.class);
        if (response == null || response.isEmpty()) {
            throw new TicketValidationException("Empty response from ticket validation");
        }

        var fields = properties.getResponse();
        Object account = response.get(fields.getAccountField());
        Object id = response.get(fields.getIdField());
        Object name = response.get(fields.getNameField());

        if (account == null || id == null) {
            throw new TicketValidationException(
                    "Ticket validation response missing required fields: "
                    + fields.getAccountField() + ", " + fields.getIdField());
        }

        return new SsoUser(
                String.valueOf(account),
                String.valueOf(id),
                name != null ? String.valueOf(name) : String.valueOf(account)
        );
    }
}
