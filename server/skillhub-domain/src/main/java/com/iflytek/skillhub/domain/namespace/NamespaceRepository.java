package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for namespace aggregates and management-oriented reads.
 */
public interface NamespaceRepository {
    Optional<Namespace> findById(Long id);
    List<Namespace> findByIdIn(List<Long> ids);
    Optional<Namespace> findBySlug(String slug);
    Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
    Namespace save(Namespace namespace);
}
