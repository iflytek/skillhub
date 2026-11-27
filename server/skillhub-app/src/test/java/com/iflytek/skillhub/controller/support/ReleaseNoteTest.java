package com.iflytek.skillhub.controller.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

class ReleaseNoteTest {

    @Test
    void keepsTheTextButTrimsTheEdges() {
        assertThat(ReleaseNote.normalize("  Fixed the parser\n")).isEqualTo("Fixed the parser");
    }

    @Test
    void treatsBlankInputAsNoNote() {
        assertThat(ReleaseNote.normalize(null)).isNull();
        assertThat(ReleaseNote.normalize("")).isNull();
        assertThat(ReleaseNote.normalize("   \n\t ")).isNull();
    }

    @Test
    void rejectsNotesAboveTheLimit() {
        assertThat(ReleaseNote.normalize("a".repeat(ReleaseNote.MAX_LENGTH))).hasSize(ReleaseNote.MAX_LENGTH);
        assertThatThrownBy(() -> ReleaseNote.normalize("a".repeat(ReleaseNote.MAX_LENGTH + 1)))
                .isInstanceOf(DomainBadRequestException.class);
    }
}
