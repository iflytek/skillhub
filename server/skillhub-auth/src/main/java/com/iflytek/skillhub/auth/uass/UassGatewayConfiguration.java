package com.iflytek.skillhub.auth.uass;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class UassGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UassGateway uassGateway(UassProperties uassProperties, MockUassLoginCoordinator mockUassLoginCoordinator) {
        return new MockUassGateway(uassProperties, mockUassLoginCoordinator);
    }

    @Bean
    @ConditionalOnMissingBean
    UassClientFacade uassClientFacade(UassGateway gateway) {
        return new UassClientFacade(gateway);
    }
}
