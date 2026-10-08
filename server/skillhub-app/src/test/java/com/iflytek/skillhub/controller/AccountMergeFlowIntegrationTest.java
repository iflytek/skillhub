package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.merge.AccountMergeRequest;
import com.iflytek.skillhub.auth.merge.AccountMergeRequestRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AccountMergeFlowIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("skillhub.builtin-skills.enabled", () -> false);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private LocalCredentialRepository localCredentialRepository;
    @Autowired private AccountMergeRequestRepository mergeRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void secondaryAccountMustApproveBeforeInitiatorCanMerge() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String primaryId = "merge-primary-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(primaryId, "Primary", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));

        String initiateResponse = mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(auth(primaryId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("secondaryIdentifier", secondaryUsername))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationToken").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(initiateResponse).path("data").path("mergeRequestId").asLong();
        String requestBody = objectMapper.writeValueAsString(java.util.Map.of("mergeRequestId", requestId));

        mockMvc.perform(get("/api/v1/account/merge/requests/{id}", requestId)
                .with(authentication(auth(primaryId))))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth(primaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/account/merge/requests/{id}", requestId)
                .with(authentication(auth(secondaryId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.primaryUserId").value(primaryId));
        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth(secondaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/account/merge/confirm")
                .with(authentication(auth(secondaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/confirm")
                .with(authentication(auth(primaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk());

        assertThat(userAccountRepository.findById(secondaryId).orElseThrow().getStatus()).isEqualTo(UserStatus.MERGED);
        assertThat(localCredentialRepository.findByUsernameIgnoreCase(secondaryUsername).orElseThrow().getUserId())
            .isEqualTo(primaryId);
    }

    @Test
    void expiredRequestCanBeReplacedUnderThePartialUniqueIndex() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String primaryId = "merge-primary-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(primaryId, "Primary", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));
        AccountMergeRequest expired = mergeRequestRepository.save(new AccountMergeRequest(
            primaryId, secondaryId, null, Instant.now().minusSeconds(1)));

        Integer indexCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_indexes where indexname = 'idx_merge_secondary_pending'", Integer.class);
        assertThat(indexCount).isEqualTo(1);

        String response = mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(auth(primaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("secondaryIdentifier", secondaryUsername))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationToken").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long newRequestId = objectMapper.readTree(response).path("data").path("mergeRequestId").asLong();

        assertThat(newRequestId).isNotEqualTo(expired.getId());
        assertThat(mergeRequestRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(AccountMergeRequest.STATUS_CANCELLED);
        assertThat(mergeRequestRepository.findById(newRequestId).orElseThrow().getStatus())
            .isEqualTo(AccountMergeRequest.STATUS_PENDING);
    }

    private static UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, userId, null, "", "local", Set.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
