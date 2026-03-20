package com.iflytek.skillhub.domain.label;

import java.util.List;
import java.util.Optional;

public interface LabelDefinitionRepository {
    Optional<LabelDefinition> findById(Long id);
    Optional<LabelDefinition> findBySlug(String slug);
    List<LabelDefinition> findAllByOrderBySortOrderAscIdAsc();
    List<LabelDefinition> findAllByOrderBySortOrderAscSlugAsc();
    List<LabelDefinition> findByVisibleInFilterTrueOrderBySortOrderAscIdAsc();
    List<LabelDefinition> findByVisibleInFilterTrueAndTypeOrderBySortOrderAscIdAsc(LabelType type);
    List<LabelDefinition> findByVisibleInFilterTrueOrderBySortOrderAscSlugAsc();
    List<LabelDefinition> findByIdIn(List<Long> ids);
    LabelDefinition save(LabelDefinition labelDefinition);
    <S extends LabelDefinition> List<S> saveAll(Iterable<S> labelDefinitions);
    void delete(LabelDefinition labelDefinition);
}
