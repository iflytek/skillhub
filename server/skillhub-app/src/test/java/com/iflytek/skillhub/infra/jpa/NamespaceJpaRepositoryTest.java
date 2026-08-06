package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class NamespaceJpaRepositoryTest {

    @Autowired
    private NamespaceJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Namespace percentNamespace;
    private Namespace underscoreNamespace;

    @BeforeEach
    void setUp() {
        percentNamespace = persist(new Namespace("percent-team", "50% Tools", "owner-1"));
        underscoreNamespace = persist(new Namespace("underscore-team", "50_Tools", "owner-1"));
        persist(new Namespace("plain-team", "Plain Tools", "owner-1"));
        Namespace archived = new Namespace("archived-percent", "50% Archived", "owner-1");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        persist(archived);
        Namespace global = new Namespace("global", "50% Global", "owner-1");
        global.setType(NamespaceType.GLOBAL);
        persist(global);
        entityManager.flush();
    }

    @Test
    void search_treatsEscapedWildcardsLiterallyAndAppliesStatus() {
        var percentPage = repository.search(
                NamespaceStatus.ACTIVE,
                NamespaceType.TEAM,
                "!%",
                null,
                PageRequest.of(0, 10)
        );
        var underscorePage = repository.searchByIdIn(
                List.of(percentNamespace.getId(), underscoreNamespace.getId()),
                NamespaceStatus.ACTIVE,
                NamespaceType.TEAM,
                "!_",
                null,
                PageRequest.of(0, 10)
        );

        assertThat(percentPage.getContent()).extracting(Namespace::getSlug)
                .containsExactly("percent-team");
        assertThat(percentPage.getTotalElements()).isEqualTo(1);
        assertThat(underscorePage.getContent()).extracting(Namespace::getSlug)
                .containsExactly("underscore-team");
    }

    @Test
    void search_acceptsNullQueryAlongsideOtherFilters() {
        var page = repository.search(
                NamespaceStatus.ACTIVE,
                NamespaceType.TEAM,
                null,
                "percent-team",
                PageRequest.of(0, 1)
        );

        assertThat(page.getContent()).extracting(Namespace::getSlug)
                .containsExactly("percent-team");
    }

    private Namespace persist(Namespace namespace) {
        entityManager.persist(namespace);
        return namespace;
    }
}
