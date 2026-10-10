package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

import java.util.List;

/** Parsed root {@value #FILE_NAME} protocol document for one Suite Bundle submission. */
public record SkillSuiteBundleManifest(
        String apiVersion,
        String kind,
        Metadata metadata,
        Spec spec
) {
    public static final String FILE_NAME = "SUITE.yaml";
    public static final String API_VERSION = "skillhub.iflytek.com/v1alpha1";
    public static final String KIND = "SkillSuiteBundle";

    public record Metadata(SkillSuiteBundleCoordinate coordinate) {
    }

    public record Spec(
            SkillSuiteBundleMode mode,
            String baseVersion,
            String version,
            String displayName,
            String summary,
            String overview,
            SkillVisibility visibility,
            String changelog,
            SkillSuiteBundleCoordinate entry,
            List<SkillSuiteBundleMember> members
    ) {
        public Spec {
            members = List.copyOf(members);
        }
    }
}
