package com.iflytek.skillhub;

import com.iflytek.skillhub.config.ProfileModerationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main Spring Boot entry point for the SkillHub backend application.
 */
@SpringBootApplication
@EnableConfigurationProperties(ProfileModerationProperties.class)
public class SkillhubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillhubApplication.class, args);
    }
}
