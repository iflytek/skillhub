package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCheckResponseTest {

    @Test
    void recordFieldsAreAccessible() {
        SkillCheckResponse response = new SkillCheckResponse(true, List.of("err"), 5, 1024L);

        assertThat(response.valid()).isTrue();
        assertThat(response.errors()).containsExactly("err");
        assertThat(response.fileCount()).isEqualTo(5);
        assertThat(response.totalSize()).isEqualTo(1024L);
    }
}
