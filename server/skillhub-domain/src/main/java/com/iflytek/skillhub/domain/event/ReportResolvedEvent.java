package com.iflytek.skillhub.domain.event;
public record ReportResolvedEvent(Long reportId, Long skillId, String handlerId, String reporterId, String action) {}
