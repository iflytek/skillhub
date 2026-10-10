package com.iflytek.skillhub.compat;

/**
 * Canonical namespace-and-slug pair used by compatibility adapters to address one skill.
 */
public record SkillCoordinate(String namespace, String slug) {}
