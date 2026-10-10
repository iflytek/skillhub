package com.iflytek.skillhub.controller.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncludeOptionsTest {

    @Test
    void includesLabels_acceptsAbsentBlankRepeatedAndCommaSeparatedInputs() {
        assertThat(IncludeOptions.includesLabels(null)).isFalse();
        assertThat(IncludeOptions.includesLabels(List.of("", " "))).isFalse();
        assertThat(IncludeOptions.includesLabels(List.of("labels"))).isTrue();
        assertThat(IncludeOptions.includesLabels(List.of(" LABELS "))).isTrue();
        assertThat(IncludeOptions.includesLabels(List.of("labels,"))).isTrue();
        assertThat(IncludeOptions.includesLabels(List.of("", "labels"))).isTrue();
    }

    @Test
    void includesLabels_rejectsUnsupportedOptions() {
        assertThatThrownBy(() -> IncludeOptions.includesLabels(List.of("labels,stats")))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.request.include.unsupported");
    }
}
