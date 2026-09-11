package com.iflytek.skillhub.domain.suite.bundle;

/** A normalized SkillHub namespace/slug coordinate. */
public record SkillSuiteBundleCoordinate(String namespace, String slug) {

    public String canonical() {
        return "@" + namespace + "/" + slug;
    }
}
