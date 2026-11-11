package com.iflytek.skillhub.dto;

/** Lightweight namespace option limited to the caller's existing review authority. */
public record ReviewScopeResponse(Long id, String slug, String displayName) {}
