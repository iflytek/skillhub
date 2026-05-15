package com.iflytek.skillhub.auth.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.config.SsoProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class SsoClientTest {

    private static final String BASE_URL = "https://sso.example.com";
    private static final String VALIDATE_PATH = "/stvalidate";
    private static final String CLIENT_URL = "https://skillhub.example.com/api/v1/auth/sso/callback";
    private static final String CLIENT_TOKEN = "test-token-123";

    private SsoProperties properties;

    @Mock
    private RestTemplate restTemplate;

    private SsoClient client;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setValidatePath(VALIDATE_PATH);
        properties.setClientUrl(CLIENT_URL);
        properties.setClientToken(CLIENT_TOKEN);

        client = new SsoClient(properties);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    void validateTicket_returnsSsoUserOnSuccess() {
        Map<String, Object> response = Map.of(
                "account", "zhangsan",
                "id", "EMP001",
                "name", "张三"
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        SsoUser user = client.validateTicket("ST-valid-ticket");

        assertThat(user.account()).isEqualTo("zhangsan");
        assertThat(user.id()).isEqualTo("EMP001");
        assertThat(user.name()).isEqualTo("张三");
    }

    @Test
    void validateTicket_returnsSsoUserWithEmptyName() {
        Map<String, Object> response = Map.of(
                "account", "lisi",
                "id", "EMP002",
                "name", ""
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        SsoUser user = client.validateTicket("ST-another-ticket");

        assertThat(user.account()).isEqualTo("lisi");
        assertThat(user.id()).isEqualTo("EMP002");
        assertThat(user.name()).isEmpty();
    }

    @Test
    void validateTicket_throwsOnNullResponse() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> client.validateTicket("ST-null"))
                .isInstanceOf(TicketValidationException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void validateTicket_throwsOnEmptyResponse() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> client.validateTicket("ST-empty"))
                .isInstanceOf(TicketValidationException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void validateTicket_throwsOnMissingRequiredFields() {
        Map<String, Object> response = Map.of(
                "name", "test"
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> client.validateTicket("ST-partial"))
                .isInstanceOf(TicketValidationException.class)
                .hasMessageContaining("missing required fields");
    }

    @Test
    void validateTicket_throwsOnMissingIdField() {
        Map<String, Object> response = Map.of(
                "account", "wangwu"
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> client.validateTicket("ST-no-id"))
                .isInstanceOf(TicketValidationException.class)
                .hasMessageContaining("missing required fields");
    }

    @Test
    void validateTicket_usesCustomResponseFields() {
        properties.getResponse().setAccountField("login");
        properties.getResponse().setIdField("employeeId");
        properties.getResponse().setNameField("fullName");

        Map<String, Object> response = Map.of(
                "login", "zhaoliu",
                "employeeId", "EMP006",
                "fullName", "赵六"
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        SsoUser user = client.validateTicket("ST-custom");

        assertThat(user.account()).isEqualTo("zhaoliu");
        assertThat(user.id()).isEqualTo("EMP006");
        assertThat(user.name()).isEqualTo("赵六");
    }

}
