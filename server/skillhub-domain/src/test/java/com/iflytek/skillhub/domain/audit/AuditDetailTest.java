package com.iflytek.skillhub.domain.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuditDetailTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // Shape — the rendered JSON must match what callers wrote by hand
    // ------------------------------------------------------------------

    @Test
    void of_rendersSingleField() {
        assertThat(AuditDetail.of("slug", "my-skill")).isEqualTo("{\"slug\":\"my-skill\"}");
    }

    @Test
    void of_preservesArgumentOrderForTwoFields() {
        assertThat(AuditDetail.of("version", "1.0.0", "targetVisibility", "PUBLIC"))
                .isEqualTo("{\"version\":\"1.0.0\",\"targetVisibility\":\"PUBLIC\"}");
    }

    @Test
    void of_keepsNumbersAndBooleansUnquoted() {
        assertThat(AuditDetail.of("count", 3)).isEqualTo("{\"count\":3}");
        assertThat(AuditDetail.of("reportId", 42L)).isEqualTo("{\"reportId\":42}");
        assertThat(AuditDetail.of("selfReview", Boolean.TRUE)).isEqualTo("{\"selfReview\":true}");
    }

    @Test
    void builder_preservesInsertionOrder() {
        assertThat(AuditDetail.builder()
                .put("comment", "ship")
                .put("selfReview", Boolean.TRUE)
                .build())
                .isEqualTo("{\"comment\":\"ship\",\"selfReview\":true}");
    }

    // ------------------------------------------------------------------
    // Escaping — the regression this class exists for
    // ------------------------------------------------------------------

    /**
     * Every one of these breaks a hand-rolled builder. The audit column is JSONB,
     * so an unescaped control character makes the insert fail after the domain
     * mutation has already committed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "line one\nline two",
            "carriage\rreturn",
            "tab\tseparated",
            "back\\slash",
            "quote \" inside",
            "both \\ and \"",
            "form\ffeed",
            "backspace\bhere",
            "中文评审意见\n第二行",
            "emoji 🚀 and \"quotes\"",
    })
    void escapedValuesRoundTripAsValidJson(String raw) throws Exception {
        String json = AuditDetail.of("comment", raw);

        JsonNode parsed = MAPPER.readTree(json);
        assertThat(parsed.get("comment").asText()).isEqualTo(raw);
    }

    /**
     * Built at runtime rather than in the annotation: a raw control character in
     * a source literal is fragile, and these are exactly the bytes JSON forbids
     * unescaped inside a string.
     */
    @Test
    void lowControlCharactersRoundTripAsValidJson() throws Exception {
        for (char c = 0; c < 0x20; c++) {
            String raw = "before" + c + "after";
            String json = AuditDetail.of("comment", raw);

            JsonNode parsed = MAPPER.readTree(json);
            assertThat(parsed.get("comment").asText())
                    .as("control character U+%04X", (int) c)
                    .isEqualTo(raw);
        }
    }

    @Test
    void newlineIsEscapedRatherThanEmbeddedRaw() {
        // The literal two-character sequence backslash-n, not a raw 0x0A.
        assertThat(AuditDetail.of("comment", "a\nb")).isEqualTo("{\"comment\":\"a\\nb\"}");
    }

    @Test
    void multiFieldPayloadWithControlCharactersStaysParseable() throws Exception {
        String json = AuditDetail.of("sourceVersion", "1.0.0\n", "targetVersion", "2.0.0\t\"x\"");

        JsonNode parsed = MAPPER.readTree(json);
        assertThat(parsed.get("sourceVersion").asText()).isEqualTo("1.0.0\n");
        assertThat(parsed.get("targetVersion").asText()).isEqualTo("2.0.0\t\"x\"");
    }

    @Test
    void everyAwkwardCharacterAtOnceStillSerializes() throws Exception {
        // Assembled from char casts: a \\uXXXX escape is expanded by the Java
        // lexer before parsing, which would put the raw byte back in the literal.
        String awkward = "" + (char) 0x00 + (char) 0x1F + "\\\"" + "\n\r\t" + "中文";

        assertThatCode(() -> AuditDetail.of("reason", awkward)).doesNotThrowAnyException();
        assertThat(MAPPER.readTree(AuditDetail.of("reason", awkward)).get("reason").asText())
                .isEqualTo(awkward);
    }

    // ------------------------------------------------------------------
    // Null handling — "no detail" must stay null, not become "{}"
    // ------------------------------------------------------------------

    @Test
    void nullValueIsSkipped() {
        assertThat(AuditDetail.of("comment", null, "selfReview", Boolean.TRUE))
                .isEqualTo("{\"selfReview\":true}");
    }

    @Test
    void allNullValuesProduceNull() {
        assertThat(AuditDetail.of("comment", null)).isNull();
        assertThat(AuditDetail.of("comment", null, "selfReview", null)).isNull();
    }

    @Test
    void emptyBuilderProducesNull() {
        assertThat(AuditDetail.builder().build()).isNull();
    }

    @Test
    void emptyStringIsStillARecordedValue() {
        // Only null is treated as absent; callers that want to drop blanks keep
        // their own isBlank() guard.
        assertThat(AuditDetail.of("comment", "")).isEqualTo("{\"comment\":\"\"}");
    }
}
