package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageEntryTest {

    @Test
    void streamingContentIsOpenedLazilyAndCanBeReopened() throws Exception {
        byte[] bytes = "streamed content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger opens = new AtomicInteger();
        PackageEntry entry = PackageEntry.streaming(
                "README.md", bytes.length, "text/markdown", () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(bytes);
                });

        assertThat(opens).hasValue(0);
        assertThat(entry.content()).isEqualTo(bytes);
        try (var input = entry.openStream()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(opens).hasValue(2);
    }

    @Test
    void materializationRejectsContentWhoseSizeChanged() {
        PackageEntry entry = PackageEntry.streaming(
                "README.md", 3, "text/markdown",
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertThatThrownBy(entry::content)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size changed");
    }
}
