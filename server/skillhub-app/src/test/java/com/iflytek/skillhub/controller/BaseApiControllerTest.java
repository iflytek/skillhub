package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BaseApiControllerTest {

    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    @Test
    void error_delegatesToResponseFactory() {
        ConcreteController controller = new ConcreteController(responseFactory);
        controller.error(400, "error.test");
    }

    private static class ConcreteController extends BaseApiController {
        ConcreteController(ApiResponseFactory responseFactory) {
            super(responseFactory);
        }
    }
}
