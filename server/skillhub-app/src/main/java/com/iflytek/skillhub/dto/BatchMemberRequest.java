package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchMemberRequest(
        @NotEmpty(message = "{validation.batch.members.notEmpty}")
        List<@Valid MemberRequest> members
) {}
