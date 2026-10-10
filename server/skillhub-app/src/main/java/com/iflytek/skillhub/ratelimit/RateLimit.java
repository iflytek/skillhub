package com.iflytek.skillhub.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares per-endpoint rate-limit settings for authenticated and anonymous callers.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String category();
    int authenticated() default 60;
    int anonymous() default 20;
    int windowSeconds() default 60;
}
