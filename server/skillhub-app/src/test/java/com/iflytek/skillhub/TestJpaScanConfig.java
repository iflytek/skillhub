package com.iflytek.skillhub;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EntityScan(basePackages = "com.iflytek.skillhub")
@EnableJpaRepositories(basePackages = "com.iflytek.skillhub")
public class TestJpaScanConfig {
}
