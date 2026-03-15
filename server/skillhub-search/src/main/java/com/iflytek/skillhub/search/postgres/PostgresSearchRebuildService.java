package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PostgresSearchRebuildService implements SearchRebuildService {

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SearchIndexService searchIndexService;

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SearchIndexService searchIndexService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.searchIndexService = searchIndexService;
    }

    @Override
    public void rebuildAll() {
        List<SkillSearchDocument> documents = skillRepository.findAll().stream()
                .filter(skill -> skill.getStatus() == SkillStatus.ACTIVE)
                .map(this::toDocument)
                .flatMap(Optional::stream)
                .toList();
        searchIndexService.batchIndex(documents);
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        List<Skill> skills = skillRepository.findByNamespaceIdAndStatus(namespaceId, SkillStatus.ACTIVE);

        for (Skill skill : skills) {
            rebuildBySkill(skill.getId());
        }
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        Optional<Skill> skillOpt = skillRepository.findById(skillId);
        if (skillOpt.isEmpty()) {
            return;
        }

        toDocument(skillOpt.get()).ifPresent(searchIndexService::index);
    }

    private String buildSearchText(Skill skill) {
        StringBuilder sb = new StringBuilder();
        if (skill.getDisplayName() != null) {
            sb.append(skill.getDisplayName()).append(" ");
        }
        sb.append(skill.getSlug()).append(" ");
        if (skill.getSummary() != null) {
            sb.append(skill.getSummary()).append(" ");
        }
        return sb.toString().trim();
    }

    private Optional<SkillSearchDocument> toDocument(Skill skill) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findById(skill.getNamespaceId());
        if (namespaceOpt.isEmpty()) {
            return Optional.empty();
        }

        Namespace namespace = namespaceOpt.get();
        String searchText = buildSearchText(skill);

        return Optional.of(new SkillSearchDocument(
                skill.getId(),
                skill.getNamespaceId(),
                namespace.getSlug(),
                skill.getOwnerId(),
                skill.getDisplayName() != null ? skill.getDisplayName() : skill.getSlug(),
                skill.getSummary(),
                "",
                searchText,
                null,
                skill.getVisibility().name(),
                skill.getStatus().name()
        ));
    }
}
