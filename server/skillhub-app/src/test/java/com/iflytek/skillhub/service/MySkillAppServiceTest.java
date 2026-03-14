package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MySkillAppServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private SkillStarRepository skillStarRepository;

    private MySkillAppService service;

    @BeforeEach
    void setUp() {
        service = new MySkillAppService(skillRepository, namespaceRepository, skillVersionRepository, skillStarRepository);
    }

    @Test
    void listMyStars_loadsAllPagesOfStars() {
        SkillStar firstStar = new SkillStar(1L, "user-1");
        ReflectionTestUtils.setField(firstStar, "createdAt", LocalDateTime.of(2026, 3, 14, 10, 0));
        SkillStar secondStar = new SkillStar(2L, "user-1");
        ReflectionTestUtils.setField(secondStar, "createdAt", LocalDateTime.of(2026, 3, 14, 11, 0));

        given(skillStarRepository.findByUserId("user-1", PageRequest.of(0, 200)))
                .willReturn(new PageImpl<>(List.of(firstStar), PageRequest.of(0, 200), 201));
        given(skillStarRepository.findByUserId("user-1", PageRequest.of(1, 200)))
                .willReturn(new PageImpl<>(List.of(secondStar), PageRequest.of(1, 200), 201));

        Skill firstSkill = new Skill(1L, "first-skill", "user-1", SkillVisibility.PUBLIC);
        firstSkill.setDisplayName("First Skill");
        firstSkill.setSummary("first summary");
        ReflectionTestUtils.setField(firstSkill, "id", 1L);
        ReflectionTestUtils.setField(firstSkill, "starCount", 1);
        ReflectionTestUtils.setField(firstSkill, "namespaceId", 101L);
        ReflectionTestUtils.setField(firstSkill, "updatedAt", LocalDateTime.of(2026, 3, 14, 10, 0));

        Skill secondSkill = new Skill(2L, "second-skill", "user-1", SkillVisibility.PUBLIC);
        secondSkill.setDisplayName("Second Skill");
        secondSkill.setSummary("second summary");
        ReflectionTestUtils.setField(secondSkill, "id", 2L);
        ReflectionTestUtils.setField(secondSkill, "starCount", 2);
        ReflectionTestUtils.setField(secondSkill, "namespaceId", 101L);
        ReflectionTestUtils.setField(secondSkill, "updatedAt", LocalDateTime.of(2026, 3, 14, 11, 0));

        given(skillRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(firstSkill, secondSkill));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(new Namespace("team-ai", "Team AI", "user-1")));

        var stars = service.listMyStars("user-1");

        assertThat(stars).hasSize(2);
        assertThat(stars.get(0).slug()).isEqualTo("second-skill");
        assertThat(stars.get(1).slug()).isEqualTo("first-skill");
    }
}
