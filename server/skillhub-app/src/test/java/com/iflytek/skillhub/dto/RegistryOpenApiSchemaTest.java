package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryOpenApiSchemaTest {

    @Test
    void resolvedSkillDeclaresEveryInstallationIdentityFieldRequired() {
        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(
                new AnnotatedType(CliResolveResponse.class));

        Schema<?> schema = resolved.schema;
        assertThat(schema.getRequired()).containsExactlyInAnyOrder(
                "namespace", "slug", "version", "versionId", "fingerprint", "downloadUrl");
        assertThat(schema.getProperties().get("versionId").getFormat()).isEqualTo("int64");
    }

    @Test
    void versionHistoryPreservesNullableChangelogAndPublicationTime() {
        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(
                new AnnotatedType(SkillVersionResponse.class));

        Schema<?> schema = resolved.schema;
        assertThat(schema.getProperties().get("changelog").getNullable()).isTrue();
        assertThat(schema.getProperties().get("publishedAt").getNullable()).isTrue();
        assertThat(schema.getProperties().get("publishedAt").getFormat()).isEqualTo("date-time");
    }
}
