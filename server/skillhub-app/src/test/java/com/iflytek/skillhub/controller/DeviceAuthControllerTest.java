package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void requestDeviceCode_withoutCsrfIsAllowedForCliFlow() throws Exception {
        given(deviceAuthService.generateDeviceCode())
                .willReturn(new DeviceCodeResponse("device-1", "USER-CODE", "http://localhost/device", 600, 5));

        mockMvc.perform(post("/api/v1/auth/device/code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deviceCode").value("device-1"));
    }

    @Test
    void pollToken_withoutCsrfIsAllowedForCliFlow() throws Exception {
        given(deviceAuthService.pollToken("device-1"))
                .willReturn(DeviceTokenResponse.success("token-1"));

        mockMvc.perform(post("/api/v1/auth/device/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"device-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("token-1"));
    }
}
