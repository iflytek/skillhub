package com.iflytek.skillhub.domain.label;

import java.util.List;

public interface LabelTranslationRepository {
    List<LabelTranslation> findByLabelId(Long labelId);
    List<LabelTranslation> findByLabelIdIn(List<Long> labelIds);
    <S extends LabelTranslation> List<S> saveAll(Iterable<S> translations);
    void deleteAll(Iterable<? extends LabelTranslation> translations);
    void deleteByLabelId(Long labelId);
}
