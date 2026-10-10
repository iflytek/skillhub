package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuiteCompositionPolicyTest {

    @Test
    void acceptsOneMemberWhenThatMemberIsTheEntry() {
        List<SkillSuiteMemberSelection> members = List.of(member(10L, 101L, "1.0.0"));

        assertThatCode(() -> SkillSuiteCompositionPolicy.validate(members, 101L))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyOneHundredMembersWithOneEntry() {
        List<SkillSuiteMemberSelection> members = LongStream.rangeClosed(1, 100)
                .mapToObj(id -> member(id, id + 1000, "1.0.0"))
                .toList();

        assertThatCode(() -> SkillSuiteCompositionPolicy.validate(members, 1001L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSuiteWithoutEntrySkill() {
        List<SkillSuiteMemberSelection> members = List.of(member(10L, 101L, "1.0.0"));

        assertThatThrownBy(() -> SkillSuiteCompositionPolicy.validate(members, null))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.entry.required");
    }

    @Test
    void rejectsEmptySuite() {
        assertThatThrownBy(() -> SkillSuiteCompositionPolicy.validate(List.of(), 101L))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.members.empty");
    }

    @Test
    void rejectsTwoVersionsOfTheSameSkill() {
        List<SkillSuiteMemberSelection> members = List.of(
                member(10L, 101L, "1.0.0"),
                member(10L, 102L, "2.0.0")
        );

        assertThatThrownBy(() -> SkillSuiteCompositionPolicy.validate(members, 101L))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.members.duplicate");
    }

    @Test
    void rejectsMoreThanOneHundredMembers() {
        List<SkillSuiteMemberSelection> members = LongStream.rangeClosed(1, 101)
                .mapToObj(id -> member(id, id + 1000, "1.0.0"))
                .toList();

        assertThatThrownBy(() -> SkillSuiteCompositionPolicy.validate(members, null))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.members.limit");
    }

    @Test
    void rejectsEntryVersionThatIsNotAMember() {
        List<SkillSuiteMemberSelection> members = List.of(member(10L, 101L, "1.0.0"));

        assertThatThrownBy(() -> SkillSuiteCompositionPolicy.validate(members, 999L))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.entry.notMember");
    }

    private SkillSuiteMemberSelection member(Long skillId, Long versionId, String version) {
        return new SkillSuiteMemberSelection(
                skillId,
                versionId,
                "global",
                "writer",
                version,
                "sha256-" + versionId
        );
    }
}
