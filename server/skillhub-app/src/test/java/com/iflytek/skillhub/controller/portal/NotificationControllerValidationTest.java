package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

class NotificationControllerValidationTest {

    @Test
    void notificationController_enablesRequestValidation() {
        assertThat(NotificationController.class).hasAnnotation(Validated.class);
    }

    @Test
    void list_appliesReasonablePageBounds() throws Exception {
        Method method = NotificationController.class.getMethod(
                "list",
                String.class,
                String.class,
                int.class,
                int.class
        );
        Parameter[] parameters = method.getParameters();

        assertThat(parameters[2].getAnnotation(Min.class)).isNotNull();
        assertThat(parameters[3].getAnnotation(Min.class)).isNotNull();
        assertThat(parameters[3].getAnnotation(Max.class)).isNotNull();
        assertThat(parameters[3].getAnnotation(Max.class).value()).isEqualTo(100);
    }
}
