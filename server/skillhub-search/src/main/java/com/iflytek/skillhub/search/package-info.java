/**
 * Search-facing ports and DTOs that provide skill discovery capabilities.
 *
 * <p>This package defines the search backend contract only: matching,
 * relevance, pagination, and visibility-aware document lookup. Application
 * assembly that enriches matches into API responses lives in
 * {@code SkillSearchAppService}, while authoritative skill detail and version
 * reads remain in {@code SkillQueryService}.
 */
package com.iflytek.skillhub.search;
