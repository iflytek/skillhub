package com.iflytek.skillhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.db.MysqlContainerBackedDataJpaTest;
import com.iflytek.skillhub.db.MysqlMigrationDataJpaTest;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

@MysqlMigrationDataJpaTest
@Testcontainers
class AuthJsonPersistenceTest extends MysqlContainerBackedDataJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private IdentityBindingRepository identityBindingRepository;

    @Test
    void persistsApiTokenScopeJsonOnMySqlSchema() {
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
    void persistsIdentityBindingExtraJsonOnMySqlSchema() {
        entityManager.persist(new UserAccount("user-binding", "Binding User", "binding@example.com", null));

        IdentityBinding binding = new IdentityBinding("user-binding", "github", "gh_42", "binding-user");
        binding.setExtraJson("{\"tenant\":\"team-a\",\"avatar\":\"https://example.com/a.png\"}");

        IdentityBinding saved = identityBindingRepository.saveAndFlush(binding);
        entityManager.clear();

        IdentityBinding reloaded = identityBindingRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getExtraJson()).isEqualTo("{\"tenant\":\"team-a\",\"avatar\":\"https://example.com/a.png\"}");
    }
}
