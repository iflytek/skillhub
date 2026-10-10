package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabelTranslationJpaRepository extends JpaRepository<LabelTranslation, Long>, LabelTranslationRepository {
    List<LabelTranslation> findByLabelId(Long labelId);
    List<LabelTranslation> findByLabelIdIn(List<Long> labelIds);
    void deleteAll(Iterable<? extends LabelTranslation> translations);
    void deleteByLabelId(Long labelId);
}
