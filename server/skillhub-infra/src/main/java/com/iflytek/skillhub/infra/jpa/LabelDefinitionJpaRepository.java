package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelDefinitionJpaRepository extends JpaRepository<LabelDefinition, Long>, LabelDefinitionRepository {
    Optional<LabelDefinition> findBySlug(String slug);
    List<LabelDefinition> findAllByOrderBySortOrderAscIdAsc();
    List<LabelDefinition> findAllByOrderBySortOrderAscSlugAsc();
    List<LabelDefinition> findByVisibleInFilterTrueOrderBySortOrderAscIdAsc();
    List<LabelDefinition> findByVisibleInFilterTrueAndTypeOrderBySortOrderAscIdAsc(LabelType type);
    List<LabelDefinition> findByVisibleInFilterTrueOrderBySortOrderAscSlugAsc();
    List<LabelDefinition> findByIdIn(List<Long> ids);
}
