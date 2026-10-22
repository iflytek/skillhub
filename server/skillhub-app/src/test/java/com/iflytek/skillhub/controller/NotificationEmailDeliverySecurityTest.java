package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.NotificationEmailDeliveryAdminPageResponse;
import com.iflytek.skillhub.service.notificationemail.NotificationEmailDeliveryQueryService;
import com.iflytek.skillhub.service.notificationemail.NotificationEmailDispatcher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="skillhub.builtin-skills.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationEmailDeliverySecurityTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",()->true);
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired MockMvc mvc;
    @MockBean NotificationEmailDeliveryQueryService service;
    @MockBean NotificationEmailDispatcher dispatcher;
    @Test void anonymousAndNonSuperAdminsCannotInspectOrRetryDeliveries() throws Exception {
        String url="/api/v1/admin/notification-email-deliveries";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(authentication(auth("ordinary","USER")))).andExpect(status().isForbidden());
        mvc.perform(post(url+"/1/retry").with(authentication(auth("skill-admin","SKILL_ADMIN"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmDuplicateRisk\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notification-email-deliveries/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void superAdminCanReadAndRetryButSessionWritesRequireCsrf() throws Exception {
        String url="/api/v1/admin/notification-email-deliveries";
        when(service.list(null,0,20)).thenReturn(new NotificationEmailDeliveryAdminPageResponse(List.of(),0,0,20,0));
        mvc.perform(get(url).with(authentication(auth("admin","SUPER_ADMIN")))).andExpect(status().isOk());
        mvc.perform(post(url+"/1/retry").with(authentication(auth("admin","SUPER_ADMIN"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmDuplicateRisk\":true}")).andExpect(status().isOk());
        verify(service).retry(1,"admin",true);
        mvc.perform(post(url+"/2/retry").with(authentication(auth("admin","SUPER_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmDuplicateRisk\":true}")).andExpect(status().isUnauthorized());
        verify(service,never()).retry(eq(2L),any(),anyBoolean());
    }
    private UsernamePasswordAuthenticationToken auth(String id,String role) {
        return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(id,id,null,null,null,Set.of(role)),null,
                List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
}
