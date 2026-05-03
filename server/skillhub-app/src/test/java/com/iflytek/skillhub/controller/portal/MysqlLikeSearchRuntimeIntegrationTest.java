package com.iflytek.skillhub.controller.portal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = SkillhubApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:sql/migration-mysql",
                "skillhub.search.engine=mysql",
                "skillhub.ratelimit.mode=memory",
                "skillhub.auth.failure-throttle.mode=memory",
                "skillhub.auth.device.enabled=false",
                "skillhub.security.scanner.enabled=false"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestRedisConfig.class)
@Testcontainers
class MysqlLikeSearchRuntimeIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillhub_search_runtime")
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
    private UserAccountRepository userAccountRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillSearchDocumentJpaRepository skillSearchDocumentJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Namespace namespace;
    private Skill skill;

    @BeforeEach
    void setUp() {
        UserAccount owner = userAccountRepository.save(new UserAccount(
                "mysql-search-owner",
                "MySQL Search Owner",
                "mysql-search-owner@example.com",
                null
        ));

        namespace = namespaceRepository.save(new Namespace(
                "mysql-search-team",
                "MySQL Search Team",
                owner.getId()
        ));

        skill = new Skill(namespace.getId(), "mysql-runtime-smoke", owner.getId(), SkillVisibility.PUBLIC);
        skill.setDisplayName("MySQL Runtime Smoke Skill");
        skill.setSummary("Searchable mysql-like runtime verification document.");
        skill.setCreatedBy(owner.getId());
        skill.setUpdatedBy(owner.getId());
        skill = skillRepository.save(skill);
        skillRepository.flush();

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", owner.getId());
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version = skillVersionRepository.save(version);
        skillVersionRepository.flush();

        skill.setLatestVersionId(version.getId());
        skill = skillRepository.save(skill);
        skillRepository.flush();

        skillSearchDocumentJpaRepository.saveAndFlush(new SkillSearchDocumentEntity(
                skill.getId(),
                namespace.getId(),
                namespace.getSlug(),
                owner.getId(),
                skill.getDisplayName(),
                skill.getSummary(),
                "mysql,smoke",
                "mysql runtime smoke searchable document",
                "",
                "PUBLIC",
                "ACTIVE"
        ));
        entityManager.clear();
    }

    @Test
    void searchEndpointReturnsMysqlLikeResultsWhenSearchableDataExists() throws Exception {
        mockMvc.perform(get("/api/web/skills")
                        .param("q", "mysql runtime smoke")
                        .param("sort", "relevance")
                        .param("page", "0")
                        .param("size", "12")
                        .with(authentication(apiAuth("mysql-search-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(skill.getId()))
                .andExpect(jsonPath("$.data.items[0].slug").value(skill.getSlug()))
                .andExpect(jsonPath("$.data.items[0].namespace").value(namespace.getSlug()))
                .andExpect(jsonPath("$.data.items[0].displayName").value(skill.getDisplayName()));
    }

    private UsernamePasswordAuthenticationToken apiAuth(String userId, String... roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of(roles)
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList()
        );
    }
}
