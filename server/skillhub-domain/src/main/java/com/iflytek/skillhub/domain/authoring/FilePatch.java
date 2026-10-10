package com.iflytek.skillhub.domain.authoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * A machine-applicable file replacement. {@code oldValue} is the exact current content
 * (null when the file does not exist yet); applying the patch replaces it with
 * {@code newValue}. {@code oldSha256} guards against applying a suggestion built on a
 * stale revision of the file.
 */
public record FilePatch(String filePath, String oldSha256, String oldValue, String newValue) {

    /** Derived helper, not part of the persisted shape. */
    @JsonIgnore
    public boolean isCreation() {
        return oldValue == null;
    }
}
