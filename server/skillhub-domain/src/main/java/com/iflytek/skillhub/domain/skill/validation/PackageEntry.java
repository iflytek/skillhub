package com.iflytek.skillhub.domain.skill.validation;

public record PackageEntry(
    String path,
    byte[] content,
    long size,
    String contentType
) {}
