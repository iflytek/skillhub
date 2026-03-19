package com.iflytek.skillhub.search;

/**
 * Read-side contract for executing skill searches against the configured search backend.
 */
public interface SearchQueryService {
    SearchResult search(SearchQuery query);
}
