package com.iflytek.skillhub.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Complete application schema; optional output path is supplied by the local generation command. */
@SpringBootTest(classes = SkillhubApplication.class,
        properties = {"skillhub.builtin-skills.enabled=false", "skillhub.bootstrap.admin.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Testcontainers
class NotificationEmailOpenApiIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ObjectStorageService storage;
    @MockBean SearchRebuildService searchRebuild;
    @MockBean JavaMailSender sender;

    @Test
    void fullApplicationExposesNewAndExistingContracts() throws Exception {
        byte[] bytes = mvc.perform(get("/v3/api-docs"))
                               .andExpect(status().isOk())
                               .andReturn()
                               .getResponse()
                               .getContentAsByteArray();
        var document = mapper.readTree(bytes);
        var paths = document.path("paths");
        assertThat(paths.size()).isGreaterThan(200);
        for (String path : new String[] {"/api/v1/notification-preferences", "/api/web/notification-preferences",
                     "/api/v1/notification-email-contact", "/api/web/notification-email-contact",
                     "/api/v1/notification-email-contact/verification-code",
                     "/api/v1/notification-email-contact/verify", "/api/v1/notification-email-preferences",
                     "/api/v1/admin/system-config/notification/email", "/api/v1/skills/{namespace}/{slug}/visibility"})
            assertThat(paths.has(path)).as(path).isTrue();
        assertThat(document.path("components").path("schemas").has("NotificationEmailContactResponse")).isTrue();
        var contact = document.path("components").path("schemas").path("NotificationEmailContactResponse");
        for (String field : new String[] {"email", "resendAvailableAt", "reason"}) {
            assertThat(contact.path("properties").path(field).path("nullable").asBoolean()).as(field).isTrue();
        }
        var schemas = document.path("components").path("schemas");
        var detail = schemas.path("NotificationEmailDeliveryDetailResponse").path("properties");
        for (String field : new String[] {"acceptedAt", "nextCursor", "mergedIntoDeliveryId"})
            assertThat(detail.path(field).path("nullable").asBoolean()).as("delivery detail %s", field).isTrue();
        assertThat(detail.has("page")).isFalse();
        assertThat(detail.has("nextOffset")).isFalse();
        var admin = schemas.path("NotificationEmailDeliveryAdminResponse").path("properties");
        for (String field : new String[] {"acceptedAt", "resultCode", "mergedIntoDeliveryId"})
            assertThat(admin.path(field).path("nullable").asBoolean()).as("delivery admin %s", field).isTrue();
        var parameters = paths.path("/api/v1/notification-email-deliveries/{id}").path("get").path("parameters");
        java.util.Set<String> names = new java.util.HashSet<>();
        parameters.forEach(parameter -> names.add(parameter.path("name").asText()));
        assertThat(names).contains("cursor", "size").doesNotContain("page", "offset");
        String output = System.getProperty("skillhub.test.openapi-output");
        if (output != null && !output.isBlank()) {
            Path target = Path.of(output);
            if (target.getParent() != null)
                Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        }
    }
}
