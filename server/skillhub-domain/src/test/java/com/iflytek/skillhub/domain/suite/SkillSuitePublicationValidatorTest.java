package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuitePublicationValidatorTest {

    @Mock private SkillSuiteVersionMemberRepository memberRepository;
    @Mock private SkillSuiteMemberStateResolver stateResolver;

    private SkillSuitePublicationValidator validator;
    private SkillSuite suite;
    private SkillSuiteVersion version;

    @BeforeEach
    void setUp() {
        validator = new SkillSuitePublicationValidator(memberRepository, stateResolver);
        suite = new SkillSuite(1L, "research-suite", "Research Suite", "author");
        version = new SkillSuiteVersion(
                10L, "1.0.0", "Research Suite", "Useful summary",
                SkillVisibility.PRIVATE, "author");
        version.setOverview("## Usage\n\nRun the entry skill first.");
    }

    @Test
    void rejectsBlankSummaryBeforePublicationMemberChecks() {
        version.setSummary("  \n  ");

        assertThatThrownBy(() -> validator.validateForPublication(suite, version))
                .isInstanceOfSatisfying(DomainBadRequestException.class,
                        exception -> assertThat(exception.messageCode())
                                .isEqualTo("error.suite.summary.required"));

        verifyNoInteractions(memberRepository, stateResolver);
    }

    @Test
    void rejectsBlankOverviewBeforePublicationMemberChecks() {
        version.setOverview("\t");

        assertThatThrownBy(() -> validator.validateForPublication(suite, version))
                .isInstanceOfSatisfying(DomainBadRequestException.class,
                        exception -> assertThat(exception.messageCode())
                                .isEqualTo("error.suite.overview.required"));

        verifyNoInteractions(memberRepository, stateResolver);
    }

    @Test
    void completeMetadataContinuesToExactMemberValidation() {
        when(memberRepository.findBySuiteVersionIdOrderByPosition(version.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> validator.validateForPublication(suite, version))
                .isInstanceOfSatisfying(DomainBadRequestException.class,
                        exception -> assertThat(exception.messageCode())
                                .isEqualTo("error.suite.members.empty"));

        verify(memberRepository).findBySuiteVersionIdOrderByPosition(version.getId());
        verifyNoInteractions(stateResolver);
    }
}
