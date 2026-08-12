package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.dto.AdminNamespaceListStatsResponse;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminNamespaceQueryRepository {
    Page<Namespace> search(String keyword, NamespaceStatus status, NamespaceType type, Pageable pageable);

    AdminNamespaceListStatsResponse stats();

    Map<Long, Long> countMembersByNamespaceId(Iterable<Long> namespaceIds);

    Map<Long, Long> countSkillsByNamespaceId(Iterable<Long> namespaceIds);
}
