package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTask;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Authorized review-center projection; filters and totals are applied before pagination. */
public interface ReviewQueueQueryRepository {
    Page<Entry> list(Filter filter, boolean platformReviewer, Set<Long> managedNamespaceIds, Pageable pageable);

    record Filter(String status, Long namespaceId, String query, String reviewKind, String originType) {}
    record Entry(ReviewTask task, String displayName, String summary, String reviewKind) {}
}
