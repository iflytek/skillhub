package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimErrorResponse(
        List<String> schemas,
        String detail,
        String status,
        String scimType
) {}
