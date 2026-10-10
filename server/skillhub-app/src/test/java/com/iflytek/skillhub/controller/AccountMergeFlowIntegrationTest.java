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
import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired private AccountMergeService mergeService;
    @Autowired private ApiTokenService apiTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
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
        String secondaryToken = apiTokenService.createToken(secondaryId, "secondary-automation", "[]").rawToken();
        String primaryToken = apiTokenService.createToken(primaryId, "primary-automation", "[]").rawToken();

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
        assertThat(apiTokenService.validateToken(secondaryToken)).isEmpty();
        assertThat(apiTokenService.validateToken(primaryToken)).isPresent();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + secondaryToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void directApiCallsCannotSpoofAnotherAccountOrBypassCsrf() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String primaryId = "merge-primary-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String outsiderId = "merge-outsider-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(primaryId, "Primary", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        userAccountRepository.save(new UserAccount(outsiderId, "Outsider", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));

        String initiateResponse = mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(auth(primaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "primaryUserId", outsiderId, "secondaryIdentifier", secondaryUsername))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(initiateResponse).path("data").path("mergeRequestId").asLong();
        AccountMergeRequest request = mergeRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getPrimaryUserId()).isEqualTo(primaryId);
        String spoofedBody = objectMapper.writeValueAsString(java.util.Map.of(
            "mergeRequestId", requestId, "secondaryUserId", secondaryId, "primaryUserId", primaryId));

        mockMvc.perform(get("/api/v1/account/merge/requests/{id}", requestId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/account/merge/requests/{id}", requestId)
                .with(authentication(auth(outsiderId))))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth(outsiderId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(spoofedBody))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/confirm")
                .with(authentication(auth(outsiderId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(spoofedBody))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/cancel")
                .with(authentication(auth(outsiderId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(spoofedBody))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth(secondaryId)))
                .contentType(MediaType.APPLICATION_JSON).content(spoofedBody))
            .andExpect(status().is4xxClientError());
        assertThat(mergeRequestRepository.findById(requestId).orElseThrow().getStatus())
            .isEqualTo(AccountMergeRequest.STATUS_PENDING);
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

    @Test
    void cancelAndConfirmCannotBothWin() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String primaryId = "merge-primary-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(primaryId, "Primary", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));
        AccountMergeRequest request = new AccountMergeRequest(
            primaryId, secondaryId, null, Instant.now().plusSeconds(1800));
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        long requestId = mergeRequestRepository.save(request).getId();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.execute("set local lock_timeout = '5s'");
                jdbcTemplate.queryForObject(
                    "select id from account_merge_request where id = ? for update", Long.class, requestId);
                locked.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            if (!locked.await(10, TimeUnit.SECONDS)) {
                releaseLock.countDown();
                lockHolder.cancel(true);
                throw new AssertionError("Could not acquire the fixture row lock");
            }
            Future<Boolean> confirm = executor.submit(() -> {
                start.await();
                try {
                    mergeService.confirm(primaryId, requestId);
                    return true;
                } catch (com.iflytek.skillhub.auth.exception.AuthFlowException expected) {
                    return false;
                }
            });
            Future<Boolean> cancel = executor.submit(() -> {
                start.await();
                try {
                    mergeService.cancel(secondaryId, requestId);
                    return true;
                } catch (com.iflytek.skillhub.auth.exception.AuthFlowException expected) {
                    return false;
                }
            });
            start.countDown();
            int waiting = 0;
            try {
                for (int attempt = 0; attempt < 50 && waiting < 2; attempt++) {
                    waiting = jdbcTemplate.queryForObject(
                        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
                            + "and query like '%account_merge_request%'", Integer.class);
                    if (waiting < 2) {
                        Thread.sleep(100);
                    }
                }
                assertThat(waiting).isGreaterThanOrEqualTo(2);
            } finally {
                releaseLock.countDown();
            }
            lockHolder.get();
            assertThat(confirm.get()).isNotEqualTo(cancel.get());
        }

        String finalStatus = mergeRequestRepository.findById(requestId).orElseThrow().getStatus();
        String credentialOwner = localCredentialRepository.findByUsernameIgnoreCase(secondaryUsername)
            .orElseThrow().getUserId();
        if (AccountMergeRequest.STATUS_COMPLETED.equals(finalStatus)) {
            assertThat(userAccountRepository.findById(secondaryId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.MERGED);
            assertThat(credentialOwner).isEqualTo(primaryId);
        } else {
            assertThat(finalStatus).isEqualTo(AccountMergeRequest.STATUS_CANCELLED);
            assertThat(userAccountRepository.findById(secondaryId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
            assertThat(credentialOwner).isEqualTo(secondaryId);
        }
    }

    @Test
    void twoApprovedDestinationsCannotBothMergeTheSameAccount() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String firstPrimaryId = "merge-first-" + suffix;
        String secondPrimaryId = "merge-second-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(firstPrimaryId, "First", null, null));
        userAccountRepository.save(new UserAccount(secondPrimaryId, "Second", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));
        AccountMergeRequest first = new AccountMergeRequest(
            firstPrimaryId, secondaryId, null, Instant.now().plusSeconds(1800));
        first.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        AccountMergeRequest second = new AccountMergeRequest(
            secondPrimaryId, secondaryId, null, Instant.now().plusSeconds(1800));
        second.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        long firstId = mergeRequestRepository.save(first).getId();
        long secondId = mergeRequestRepository.save(second).getId();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.execute("set local lock_timeout = '5s'");
                jdbcTemplate.queryForObject(
                    "select id from user_account where id = ? for update", String.class, secondaryId);
                locked.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            if (!locked.await(10, TimeUnit.SECONDS)) {
                releaseLock.countDown();
                lockHolder.cancel(true);
                throw new AssertionError("Could not acquire the account row lock");
            }
            Future<Boolean> firstConfirm = executor.submit(() -> confirmOrReject(firstPrimaryId, firstId));
            Future<Boolean> secondConfirm = executor.submit(() -> confirmOrReject(secondPrimaryId, secondId));
            int waiting = 0;
            try {
                for (int attempt = 0; attempt < 50 && waiting < 2; attempt++) {
                    waiting = jdbcTemplate.queryForObject(
                        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
                            + "and query like '%user_account%'", Integer.class);
                    if (waiting < 2) {
                        Thread.sleep(100);
                    }
                }
                assertThat(waiting).isGreaterThanOrEqualTo(2);
            } finally {
                releaseLock.countDown();
            }
            lockHolder.get();
            assertThat(firstConfirm.get()).isNotEqualTo(secondConfirm.get());
        }

        String winner = localCredentialRepository.findByUsernameIgnoreCase(secondaryUsername)
            .orElseThrow().getUserId();
        assertThat(winner).isIn(firstPrimaryId, secondPrimaryId);
        assertThat(userAccountRepository.findById(secondaryId).orElseThrow().getMergedToUserId())
            .isEqualTo(winner);
        assertThat(List.of(firstId, secondId).stream()
            .filter(id -> AccountMergeRequest.STATUS_COMPLETED.equals(
                mergeRequestRepository.findById(id).orElseThrow().getStatus())).count()).isEqualTo(1);
    }

    private boolean confirmOrReject(String primaryUserId, long requestId) {
        try {
            mergeService.confirm(primaryUserId, requestId);
            return true;
        } catch (com.iflytek.skillhub.auth.exception.AuthFlowException expected) {
            return false;
        }
    }

    @Test
    void secondaryCancellationAfterApprovalBlocksConfirmation() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String primaryId = "merge-primary-" + suffix;
        String secondaryId = "merge-secondary-" + suffix;
        String secondaryUsername = "merge-" + suffix;
        userAccountRepository.save(new UserAccount(primaryId, "Primary", null, null));
        userAccountRepository.save(new UserAccount(secondaryId, "Secondary", null, null));
        localCredentialRepository.save(new LocalCredential(secondaryId, secondaryUsername, "hash"));
        AccountMergeRequest request = mergeRequestRepository.save(new AccountMergeRequest(
            primaryId, secondaryId, null, Instant.now().plusSeconds(1800)));
        String body = objectMapper.writeValueAsString(java.util.Map.of("mergeRequestId", request.getId()));

        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth(secondaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/account/merge/cancel")
                .with(authentication(auth(secondaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/account/merge/confirm")
                .with(authentication(auth(primaryId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());

        assertThat(mergeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
            .isEqualTo(AccountMergeRequest.STATUS_CANCELLED);
        assertThat(userAccountRepository.findById(secondaryId).orElseThrow().getStatus())
            .isEqualTo(UserStatus.ACTIVE);
        assertThat(localCredentialRepository.findByUsernameIgnoreCase(secondaryUsername).orElseThrow().getUserId())
            .isEqualTo(secondaryId);
    }

    private static UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, userId, null, "", "local", Set.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
