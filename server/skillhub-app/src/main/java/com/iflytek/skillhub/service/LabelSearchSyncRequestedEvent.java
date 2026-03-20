package com.iflytek.skillhub.service;

import java.util.List;

public record LabelSearchSyncRequestedEvent(List<Long> skillIds) {}
