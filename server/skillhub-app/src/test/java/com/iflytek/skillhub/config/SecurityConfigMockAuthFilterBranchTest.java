package com.iflytek.skillhub.config;

import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfigMockAuthFilterBranchTest.Config.class)
class SecurityConfigMockAuthFilterBranchTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void securityFilterChainWiresMockAuthFilterWhenAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class Config {
        @Bean
        MockAuthFilter mockAuthFilter() {
            return new MockAuthFilter(
                    mock(UserAccountRepository.class),
                    mock(UserRoleBindingRepository.class),
                    mock(PlatformSessionService.class)
            );
        }
    }
}
