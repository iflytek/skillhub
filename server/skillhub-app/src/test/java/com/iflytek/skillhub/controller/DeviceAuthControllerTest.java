package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void requestDeviceCode_returns_code() throws Exception {
        DeviceCodeResponse response = new DeviceCodeResponse(
            "device_abc123",
            "ABCD-1234",
            "https://skillhub.example.com/device",
            900,
            5
        );

        given(deviceAuthService.generateDeviceCode()).willReturn(response);

        mockMvc.perform(post("/api/v1/cli/auth/device/code")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deviceCode").value("device_abc123"))
            .andExpect(jsonPath("$.data.userCode").value("ABCD-1234"))
            .andExpect(jsonPath("$.data.verificationUri").value("https://skillhub.example.com/device"))
            .andExpect(jsonPath("$.data.expiresIn").value(900))
            .andExpect(jsonPath("$.data.interval").value(5));
    }

    @Test
    void pollToken_returns_pending() throws Exception {
        DeviceTokenResponse response = DeviceTokenResponse.pending();

        given(deviceAuthService.pollToken("device_abc123")).willReturn(response);

        mockMvc.perform(post("/api/v1/cli/auth/device/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\": \"device_abc123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.error").value("authorization_pending"))
            .andExpect(jsonPath("$.data.accessToken").isEmpty())
            .andExpect(jsonPath("$.data.tokenType").isEmpty());
    }
}
