package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Revalidates exact members immediately before each Suite publication transition. */
@Service
public class SkillSuitePublicationValidator {

    private final SkillSuiteVersionMemberRepository memberRepository;
    private final SkillSuiteMemberStateResolver stateResolver;

    public SkillSuitePublicationValidator(
            SkillSuiteVersionMemberRepository memberRepository,
            SkillSuiteMemberStateResolver stateResolver
    ) {
        this.memberRepository = memberRepository;
        this.stateResolver = stateResolver;
    }

    public SkillSuiteAvailability validate(SkillSuite suite, SkillSuiteVersion version) {
        List<SkillSuiteVersionMember> members =
                memberRepository.findBySuiteVersionIdOrderByPosition(version.getId());
        if (members.isEmpty()) {
            throw new DomainBadRequestException("error.suite.members.empty");
        }
        if (members.stream().filter(SkillSuiteVersionMember::isEntry).count() != 1) {
            throw new DomainBadRequestException("error.suite.entry.required");
        }
        SkillSuiteAvailability availability = SkillSuiteAvailability.evaluate(
                suite.getNamespaceId(), version.getVisibility(), stateResolver.resolve(members));
        if (!availability.available()) {
            String reasons = availability.blockedMembers().stream()
                    .map(blocked -> blocked.skillVersionId() + ":" + blocked.reason())
                    .collect(java.util.stream.Collectors.joining(","));
            throw new DomainBadRequestException("error.suite.members.unavailable", reasons);
        }
        return availability;
    }
}
