package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.support.PostgresTestImage;
import java.util.Set;
import java.util.List;
import java.time.Instant;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.mock.web.MockMultipartFile;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.review.ReviewDecisionMethod;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the full filter chain keeps team-only automatic publication grants session-only.
 *
 * <p>Member mutations take PostgreSQL row locks, and this also validates the V125 schema against
 * the JPA mappings after Flyway migrates an empty database.
 */
@SpringBootTest(classes = SkillhubApplication.class, properties = {
        "skillhub.builtin-skills.enabled=false", "skillhub.ratelimit.enabled=false",
        "skillhub.security.scanner.enabled=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TeamAutoPublicationGrantSecurityTest.Configuration.class})
@Testcontainers
class TeamAutoPublicationGrantSecurityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @MockBean private SearchEmbeddingService searchEmbeddingService;
    @MockBean private ObjectStorageService storage;
    @MockBean private ScanTaskProducer scanTaskProducer;
    // Keep the real scan/domain callback while isolating Redis transport and background consumption.
    @MockBean private org.redisson.api.RedissonClient redissonClient;
    @MockBean private com.iflytek.skillhub.stream.ScanTaskConsumer scanTaskConsumer;
    @Autowired private SkillRepository skills;
    @Autowired private SkillVersionRepository versions;
    @Autowired private ReviewTaskRepository reviews;
    @Autowired private SecurityAuditRepository audits;
    @Autowired private SecurityScanService scans;
    @Autowired private EventRecorder events;

    @TestConfiguration
    static class Configuration {
        @Bean EventRecorder teamPublicationEvents() { return new EventRecorder(); }
    }

    static class EventRecorder {
        private final List<SkillPublishedEvent> publications = new CopyOnWriteArrayList<>();
        private final List<ReviewSubmittedEvent> handoffs = new CopyOnWriteArrayList<>();
        @EventListener public void published(SkillPublishedEvent event) { publications.add(event); }
        @EventListener public void submitted(ReviewSubmittedEvent event) { handoffs.add(event); }
        long publications(Long versionId) {
            return publications.stream().filter(event -> event.versionId().equals(versionId)).count();
        }
        long handoffs(Long versionId) {
            return handoffs.stream().filter(event -> event.versionId().equals(versionId)).count();
        }
    }

    private static final String EVERY_CURRENT_SCOPE =
            "[\"skill:read\",\"skill:submit\",\"skill:publish-private\",\"skill:manage\",\"skill:delete\",\"token:manage\"]";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiTokenService apiTokenService;
    @Autowired private UserAccountRepository users;
    @Autowired private NamespaceRepository namespaces;
    @Autowired private NamespaceMemberRepository members;

    private String owner;
    private String publisher;
    private String slug;
    private Long namespaceId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = "grant-owner-" + suffix;
        publisher = "grant-member-" + suffix;
        slug = "grant-" + suffix;
        users.save(new UserAccount(owner, "Owner", owner + "@example.com", null));
        users.save(new UserAccount(publisher, "Publisher", publisher + "@example.com", null));
        Namespace namespace = namespaces.save(new Namespace(slug, "Grant team", owner));
        namespaceId = namespace.getId();
        members.save(new NamespaceMember(namespaceId, owner, NamespaceRole.OWNER));
        members.save(new NamespaceMember(namespaceId, publisher, NamespaceRole.MEMBER));
    }

    @Test
    void ownerTokenWithEveryCurrentScopeCannotGrantOrRevoke() throws Exception {
        String rawToken = apiTokenService.createToken(owner, "grant-" + UUID.randomUUID(), EVERY_CURRENT_SCOPE)
                .rawToken();
        for (String prefix : new String[] {"/api/web", "/api/v1"}) {
            for (boolean granted : new boolean[] {true, false}) {
                mockMvc.perform(put(prefix + "/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"granted\":" + granted + "}"))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .isTeamAutoPublicationGranted()).isFalse();
    }

    @Test
    void ownerSessionGrantsAndRevokesThroughMemberManagement() throws Exception {
        MockHttpSession session = session(owner);
        String route = "/api/web/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication";

        mockMvc.perform(put(route).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamAutoPublicationGranted").value(true))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNotNull();

        mockMvc.perform(put(route).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamAutoPublicationGranted").value(false));
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNull();
    }

    @Test
    void ordinaryMemberSessionCannotGrantItself() throws Exception {
        mockMvc.perform(put("/api/web/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication")
                        .session(session(publisher)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isForbidden());
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .isTeamAutoPublicationGranted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"/api/v1,REVOKED", "/api/web,REVOKED", "/api/v1,EXPIRED", "/api/web,EXPIRED"})
    void acceptedWorkRetainsSubmitCeilingAfterTokenBecomesInvalid(String prefix, String invalidation) throws Exception {
        grant(true);
        var credential = apiTokenService.createToken(publisher, UUID.randomUUID().toString(),
                "[\"skill:read\",\"skill:submit\"]",
                invalidation.equals("EXPIRED") ? Instant.now().plusSeconds(8).toString() : null);
        Submitted submitted = upload(prefix, credential.rawToken());
        var captured = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(captured.isSubmissionMaySubmit()).isTrue();
        UUID grantId = captured.getAutoPublicationGrantId();
        assertThat(grantId).isNotNull();
        assertThat(versions.findById(submitted.versionId()).orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.SCANNING);
        if (invalidation.equals("REVOKED")) {
            mockMvc.perform(delete("/api/v1/tokens/" + credential.entity().getId())
                            .session(session(publisher)).with(csrf()))
                    .andExpect(status().isNoContent());
        } else {
            await().atMost(Duration.ofSeconds(12)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                    mockMvc.perform(get("/api/cli/v1/auth/whoami")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.rawToken()))
                            .andExpect(status().isUnauthorized()));
        }
        mockMvc.perform(get("/api/cli/v1/auth/whoami")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.rawToken()))
                .andExpect(status().isUnauthorized());
        // Real filters must also reject a new write, without creating a second version.
        mockMvc.perform(multipart(prefix + "/skills/" + slug + "/publish").file(archive("2.0.0"))
                        .param("visibility", "NAMESPACE_ONLY").param("confirmWarnings", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.rawToken()))
                .andExpect(status().isUnauthorized());
        assertThat(versions.findBySkillIdAndVersion(submitted.skillId(), "2.0.0")).isEmpty();
        complete(submitted);
        complete(submitted);
        var approved = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(approved.getDecisionMethod()).isEqualTo(ReviewDecisionMethod.SYSTEM_AUTOMATIC);
        assertThat(approved.getReviewedBy()).isNull();
        assertThat(approved.getAutoPublicationGrantId()).isEqualTo(grantId);
        assertThat(approved.isSubmissionMaySubmit()).isTrue();
        assertThat(approved.getDecisionScanTaskId()).isEqualTo(submitted.scanTaskId());
        assertThat(versions.findById(submitted.versionId()).orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(skills.findById(submitted.skillId()).orElseThrow().getLatestVersionId())
                .isEqualTo(submitted.versionId());
        assertThat(events.publications(submitted.versionId())).isEqualTo(1);
        assertThat(events.handoffs(submitted.versionId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void revokeRegrantAndReplayCannotReviveAcceptedWork(boolean revokeToken) throws Exception {
        grant(true);
        var credential = apiTokenService.createToken(publisher, UUID.randomUUID().toString(),
                "[\"skill:read\",\"skill:submit\"]");
        Submitted submitted = upload("/api/v1", credential.rawToken());
        UUID originalGrant = reviews.findById(submitted.reviewId()).orElseThrow().getAutoPublicationGrantId();
        if (revokeToken) {
            apiTokenService.revokeToken(credential.entity().getId(), publisher);
            mockMvc.perform(get("/api/cli/v1/auth/whoami")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.rawToken()))
                    .andExpect(status().isUnauthorized());
        }
        grant(false);
        grant(true);
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNotEqualTo(originalGrant);
        assertThat(versions.findById(submitted.versionId()).orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.SCANNING);
        assertThat(events.handoffs(submitted.versionId())).isEqualTo(1);
        complete(submitted);
        complete(submitted);
        var pending = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(pending.isAutoPublicationCancelled()).isTrue();
        assertThat(pending.isManualReviewNotified()).isTrue();
        assertThat(pending.isSubmissionMaySubmit()).isTrue();
        assertThat(pending.getAutoPublicationGrantId()).isEqualTo(originalGrant);
        assertThat(pending.getDecisionMethod()).isNull();
        assertThat(pending.getReviewedBy()).isNull();
        assertThat(versions.findById(submitted.versionId()).orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(skills.findById(submitted.skillId()).orElseThrow().getLatestVersionId()).isNull();
        assertThat(events.publications(submitted.versionId())).isZero();
        assertThat(events.handoffs(submitted.versionId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web", "/api/cli/v1"})
    void memberGrantCannotSupplyMissingTokenSubmissionCapability(String prefix) throws Exception {
        grant(true);
        String readOnly = apiTokenService.createToken(publisher, UUID.randomUUID().toString(),
                "[\"skill:read\"]").rawToken();
        mockMvc.perform(multipart(prefix + "/skills/" + slug + "/publish").file(archive("1.0.0"))
                        .param("visibility", "NAMESPACE_ONLY").param("confirmWarnings", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("error.apiToken.scope.missing"));
        assertThat(skills.findByNamespaceIdAndSlugAndOwnerId(namespaceId, "auto-lifecycle", publisher)).isEmpty();
    }

    private void grant(boolean granted) throws Exception {
        mockMvc.perform(put("/api/web/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication")
                        .session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":" + granted + "}"))
                .andExpect(status().isOk());
    }

    private Submitted upload(String prefix, String rawToken) throws Exception {
        mockMvc.perform(multipart(prefix + "/skills/" + slug + "/publish").file(archive("1.0.0"))
                        .param("visibility", "NAMESPACE_ONLY").param("confirmWarnings", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                .andExpect(status().isOk());
        var skill = skills.findByNamespaceIdAndSlugAndOwnerId(namespaceId, "auto-lifecycle", publisher).orElseThrow();
        var version = versions.findBySkillIdAndVersion(skill.getId(), "1.0.0").orElseThrow();
        var review = reviews.findBySkillVersionIdAndStatus(version.getId(), ReviewTaskStatus.PENDING).orElseThrow();
        var audit = audits.findLatestActiveByVersionIdAndScannerType(version.getId(), ScannerType.SKILL_SCANNER)
                .orElseThrow();
        return new Submitted(skill.getId(), version.getId(), review.getId(), audit.getTaskId());
    }

    private void complete(Submitted submitted) {
        // Controlled callback through the real scan service; this is not a real Scanner process.
        scans.processScanResult(submitted.scanTaskId(), submitted.versionId(), ScannerType.SKILL_SCANNER,
                new SecurityScanResponse("controlled-" + submitted.scanTaskId(), SecurityVerdict.SAFE,
                        0, null, List.of(), 0.1));
    }

    private static MockMultipartFile archive(String version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(("---\nname: auto-lifecycle\ndescription: Summarize supplied text for a local authorization test.\n"
                    + "version: " + version + "\n---\n# Local test\nSummarize only the supplied text.\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile("file", "skill.zip", "application/zip", bytes.toByteArray());
    }

    private record Submitted(Long skillId, Long versionId, Long reviewId, String scanTaskId) {}

    private MockHttpSession session(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, userId, userId + "@example.com", null,
                "github", Set.of("USER"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("platformPrincipal", principal);
        return session;
    }
}
