package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.RuntimeBinding;
import com.iflytek.skillhub.domain.authoring.RuntimeBindingRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for per-draft runtime bindings.
 */
@Repository
public interface RuntimeBindingJpaRepository extends JpaRepository<RuntimeBinding, Long>,
        RuntimeBindingRepository {

    Optional<RuntimeBinding> findByDraftId(Long draftId);

    void deleteByDraftId(Long draftId);
}
