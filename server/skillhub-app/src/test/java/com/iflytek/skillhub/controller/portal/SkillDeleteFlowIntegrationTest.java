package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:sql/migration-mysql"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestRedisConfig.class)
@Testcontainers
class SkillDeleteFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillhub_skill_delete")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillFileRepository skillFileRepository;

    @Autowired
    private UserAccountJpaRepository userAccountRepository;

    @MockBean
    private SearchIndexService searchIndexService;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void portalDeleteById_removesPersistedSkillGraphForOwner() throws Exception {
        PersistedSkillGraph graph = createSkillGraph("owner-1");

        mockMvc.perform(delete("/api/web/skills/id/" + graph.skill().getId())
                        .with(authentication(portalAuth("owner-1", "USER")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(graph.skill().getId()))
                .andExpect(jsonPath("$.data.namespace").value(graph.namespace().getSlug()))
                .andExpect(jsonPath("$.data.slug").value(graph.skill().getSlug()))
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(skillRepository.findById(graph.skill().getId())).isEmpty();
        assertThat(skillVersionRepository.findBySkillId(graph.skill().getId())).isEmpty();
        assertThat(skillFileRepository.findByVersionId(graph.version().getId())).isEmpty();
        verify(searchIndexService).remove(graph.skill().getId());
        verify(objectStorageService).deleteObjects(argThat(keys ->
                keys.contains(graph.file().getStorageKey())
                        && keys.contains("packages/" + graph.skill().getId() + "/" + graph.version().getId() + "/bundle.zip")));
    }

    @Test
    void portalDeleteById_rejectsNonOwnerAndKeepsPersistedSkillGraph() throws Exception {
        PersistedSkillGraph graph = createSkillGraph("owner-1");

        mockMvc.perform(delete("/api/web/skills/id/" + graph.skill().getId())
                        .with(authentication(portalAuth("user-2", "USER")))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertThat(skillRepository.findById(graph.skill().getId())).isPresent();
        assertThat(skillVersionRepository.findBySkillId(graph.skill().getId())).hasSize(1);
        assertThat(skillFileRepository.findByVersionId(graph.version().getId())).hasSize(1);
        verify(searchIndexService, never()).remove(graph.skill().getId());
        verify(objectStorageService, never()).deleteObjects(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void adminDeleteById_removesPersistedSkillGraphForSuperAdmin() throws Exception {
        PersistedSkillGraph graph = createSkillGraph("owner-1");
        ensureUser("super-1", "Super Admin", "super-1@example.com");

        mockMvc.perform(delete("/api/v1/skills/id/" + graph.skill().getId())
                        .with(authentication(apiAuth("super-1", "SUPER_ADMIN")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(graph.skill().getId()))
                .andExpect(jsonPath("$.data.namespace").value(graph.namespace().getSlug()))
                .andExpect(jsonPath("$.data.slug").value(graph.skill().getSlug()))
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(skillRepository.findById(graph.skill().getId())).isEmpty();
        assertThat(skillVersionRepository.findBySkillId(graph.skill().getId())).isEmpty();
        assertThat(skillFileRepository.findByVersionId(graph.version().getId())).isEmpty();
        verify(searchIndexService).remove(graph.skill().getId());
    }

    private PersistedSkillGraph createSkillGraph(String ownerId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ensureUser(ownerId, "Skill Owner", ownerId + "-" + suffix + "@example.com");
        Namespace namespace = namespaceRepository.save(new Namespace("global-delete-" + suffix, "Global Delete " + suffix, ownerId));

        Skill skill = new Skill(namespace.getId(), "demo-skill-" + suffix, ownerId, SkillVisibility.PUBLIC);
        skill.setCreatedBy(ownerId);
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);
        skillRepository.flush();

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", ownerId);
        version = skillVersionRepository.save(version);
        skillVersionRepository.flush();

        skill.setLatestVersionId(version.getId());
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);
        skillRepository.flush();

        SkillFile file = skillFileRepository.save(new SkillFile(
                version.getId(),
                "SKILL.md",
                12L,
                "text/markdown",
                "sha-" + suffix,
                "skills/" + skill.getId() + "/" + version.getId() + "/SKILL.md"
        ));

        return new PersistedSkillGraph(namespace, skill, version, file);
    }

    private void ensureUser(String userId, String displayName, String email) {
        if (!userAccountRepository.existsById(userId)) {
            userAccountRepository.saveAndFlush(new UserAccount(userId, displayName, email, null));
        }
    }

    private UsernamePasswordAuthenticationToken portalAuth(String userId, String... roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of(roles)
        );
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private UsernamePasswordAuthenticationToken apiAuth(String userId, String... roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "api_token",
                Set.of(roles)
        );
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        authorities.add(new SimpleGrantedAuthority("SCOPE_skill:delete"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private record PersistedSkillGraph(Namespace namespace, Skill skill, SkillVersion version, SkillFile file) {
    }
}
