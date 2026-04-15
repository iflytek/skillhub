package com.iflytek.skillhub;

import com.iflytek.skillhub.config.ProfileFieldPolicyProperties;
import com.iflytek.skillhub.config.ProfileModerationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot entry point for the SkillHub backend application.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ProfileModerationProperties.class, ProfileFieldPolicyProperties.class})
public class SkillhubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillhubApplication.class, args);
    }
}
