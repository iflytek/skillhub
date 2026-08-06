package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed namespace repository that also fulfills the domain namespace repository contract.
 */
@Repository
public interface NamespaceJpaRepository
        extends JpaRepository<Namespace, Long>, NamespaceRepository {
    List<Namespace> findByIdIn(List<Long> ids);
    Optional<Namespace> findBySlug(String slug);
    Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);

    @Override
    @Query("""
            SELECT n
            FROM Namespace n
            WHERE (:status IS NULL OR n.status = :status)
              AND (:type IS NULL OR n.type = :type)
              AND (
                :query IS NULL
                OR lower(n.slug) LIKE lower(concat('%', cast(:query as string), '%')) ESCAPE '!'
                OR lower(n.displayName) LIKE lower(concat('%', cast(:query as string), '%')) ESCAPE '!'
              )
              AND (:slug IS NULL OR n.slug = :slug)
            """)
    Page<Namespace> search(@Param("status") NamespaceStatus status,
                           @Param("type") NamespaceType type,
                           @Param("query") String query,
                           @Param("slug") String slug,
                           Pageable pageable);

    @Override
    @Query("""
            SELECT n
            FROM Namespace n
            WHERE n.id IN :ids
              AND (:status IS NULL OR n.status = :status)
              AND (:type IS NULL OR n.type = :type)
              AND (
                :query IS NULL
                OR lower(n.slug) LIKE lower(concat('%', cast(:query as string), '%')) ESCAPE '!'
                OR lower(n.displayName) LIKE lower(concat('%', cast(:query as string), '%')) ESCAPE '!'
              )
              AND (:slug IS NULL OR n.slug = :slug)
            """)
    Page<Namespace> searchByIdIn(@Param("ids") List<Long> ids,
                                 @Param("status") NamespaceStatus status,
                                 @Param("type") NamespaceType type,
                                 @Param("query") String query,
                                 @Param("slug") String slug,
                                 Pageable pageable);
}
