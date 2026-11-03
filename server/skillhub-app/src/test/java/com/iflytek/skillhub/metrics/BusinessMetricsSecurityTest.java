package com.iflytek.skillhub.metrics;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(properties = "SKILLHUB_ACTUATOR_EXPOSURE_INCLUDE=health,info,metrics,prometheus")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class BusinessMetricsSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired MeterRegistry registry;
    @MockBean NamespaceMemberRepository namespaceMemberRepository;
    @MockBean DeviceAuthService deviceAuthService;

    static RequestPostProcessor session(String... roles) {
        var principal = new PlatformPrincipal("synthetic-metrics-user", "Metrics test", null, null, "local", Set.of(roles));
        var authorities = java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test
    void optInExposesOnlyMetricsToAuthorizedSessions() throws Exception {
        registry.counter("skillhub.business.logs.enqueued").increment();
        for (String path : List.of("/actuator/metrics", "/actuator/metrics/skillhub.business.logs.enqueued", "/actuator/prometheus")) {
            mockMvc.perform(get(path).with(session("SUPER_ADMIN"))).andExpect(status().isOk());
            mockMvc.perform(get(path).with(session())).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/actuator/env").with(session("SUPER_ADMIN"))).andExpect(status().isNotFound());
    }
}
