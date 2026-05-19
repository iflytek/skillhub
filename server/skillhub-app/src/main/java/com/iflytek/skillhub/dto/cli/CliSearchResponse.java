package com.iflytek.skillhub.dto.cli;

import java.util.List;

public record CliSearchResponse(List<CliSearchItemResponse> items, long total, int limit) {}
