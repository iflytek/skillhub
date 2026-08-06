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
    Page<Namespace> findAll(Pageable pageable);
    Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
    Page<Namespace> search(
            NamespaceStatus status,
            NamespaceType type,
            String query,
            String slug,
            Pageable pageable
    );
    Page<Namespace> searchByIdIn(
            List<Long> ids,
            NamespaceStatus status,
            NamespaceType type,
            String query,
            String slug,
            Pageable pageable
    );
    Namespace save(Namespace namespace);
    void delete(Namespace namespace);
}
