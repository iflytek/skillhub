package com.iflytek.skillhub;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class SkillhubApplicationTest {

    @Test
    void main_startsSpringApplication() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            SkillhubApplication.main(new String[]{});
            spring.verify(() -> SpringApplication.run(SkillhubApplication.class, new String[]{}));
        }
    }
}
