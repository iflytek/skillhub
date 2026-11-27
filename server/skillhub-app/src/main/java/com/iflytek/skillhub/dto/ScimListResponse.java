package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScimListResponse(
        List<String> schemas,
        int totalResults,
        int startIndex,
        int itemsPerPage,
        @JsonProperty("Resources") List<ScimUserResource> resources
) {}
