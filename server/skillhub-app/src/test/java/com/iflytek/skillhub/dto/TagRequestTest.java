package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagRequestTest {

    @Test
    void recordFieldsAreAccessible() {
        TagRequest request = new TagRequest("stable", "1.0.0");

        assertThat(request.tagName()).isEqualTo("stable");
        assertThat(request.targetVersion()).isEqualTo("1.0.0");
    }
}
