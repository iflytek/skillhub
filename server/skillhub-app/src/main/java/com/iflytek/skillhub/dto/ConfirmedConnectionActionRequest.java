package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ConfirmedConnectionActionRequest(@NotNull @AssertTrue Boolean confirmed) {
}
