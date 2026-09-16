package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates rules that depend only on a Suite draft's selected members.
 */
public final class SkillSuiteCompositionPolicy {

    public static final int MAX_MEMBERS = 100;

    private SkillSuiteCompositionPolicy() {
    }

    public static void validate(List<SkillSuiteMemberSelection> members, Long entrySkillVersionId) {
        if (members.isEmpty()) {
            throw new DomainBadRequestException("error.suite.members.empty");
        }
        if (members.size() > MAX_MEMBERS) {
            throw new DomainBadRequestException("error.suite.members.limit", MAX_MEMBERS);
        }
        Set<Long> skillIds = new HashSet<>();
        if (entrySkillVersionId == null) {
            throw new DomainBadRequestException("error.suite.entry.required");
        }
        boolean entryFound = false;
        for (SkillSuiteMemberSelection member : members) {
            if (!skillIds.add(member.skillId())) {
                throw new DomainBadRequestException("error.suite.members.duplicate");
            }
            if (member.skillVersionId().equals(entrySkillVersionId)) {
                entryFound = true;
            }
        }
        if (!entryFound) {
            throw new DomainBadRequestException("error.suite.entry.notMember");
        }
    }
}
