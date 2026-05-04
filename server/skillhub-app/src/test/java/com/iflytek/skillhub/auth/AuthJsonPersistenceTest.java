package com.iflytek.skillhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:sql/migration-mysql"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuthJsonPersistenceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillhub_auth_json")
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
    private EntityManager entityManager;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private IdentityBindingRepository identityBindingRepository;

    @Test
    void persistsApiTokenScopeJsonWithoutPostgresSpecificColumnDefinition() {
        entityManager.persist(new UserAccount("user-token", "Token User", "token@example.com", null));

        ApiToken token = new ApiToken(
                "user-token",
                "cli",
                "sk_123456",
                "hash-token-1",
                "[\"skill:publish\",\"token:manage\"]"
        );

        ApiToken saved = apiTokenRepository.saveAndFlush(token);
        entityManager.clear();

        ApiToken reloaded = apiTokenRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getScopeJson()).isEqualTo("[\"skill:publish\",\"token:manage\"]");
    }

    @Test
    void persistsIdentityBindingExtraJsonWithoutPostgresSpecificColumnDefinition() {
        entityManager.persist(new UserAccount("user-binding", "Binding User", "binding@example.com", null));

        IdentityBinding binding = new IdentityBinding("user-binding", "github", "gh_42", "binding-user");
        binding.setExtraJson("{\"tenant\":\"team-a\",\"avatar\":\"https://example.com/a.png\"}");

        IdentityBinding saved = identityBindingRepository.saveAndFlush(binding);
        entityManager.clear();

        IdentityBinding reloaded = identityBindingRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getExtraJson()).isEqualTo("{\"tenant\":\"team-a\",\"avatar\":\"https://example.com/a.png\"}");
    }
}
