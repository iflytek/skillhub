package com.iflytek.skillhub.domain.event;
public record ReportSubmittedEvent(Long reportId, Long skillId, String reporterId) {}
