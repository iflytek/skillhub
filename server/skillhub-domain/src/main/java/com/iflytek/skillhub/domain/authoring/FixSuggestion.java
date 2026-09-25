package com.iflytek.skillhub.domain.authoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A fix suggestion attached to a validation finding. Suggestions are proposals only:
 * they are never applied without explicit user confirmation, and applying them always
 * produces a new draft revision so history stays auditable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixSuggestion(
        String description,
        @JsonProperty("patches") List<FilePatch> patches
) {

    public List<FilePatch> safePatches() {
        return patches == null ? List.of() : List.copyOf(patches);
    }
}
