package com.iflytek.skillhub.support;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL image used by Testcontainers-backed tests.
 *
 * <p>Defaults to the deployment default ({@code postgres:16-alpine}). CI runs the backend suite a
 * second time with {@code -Dskillhub.test.postgres.image=postgres:17-alpine} so every PostgreSQL
 * test, including Flyway migrations and application startup, also runs on PostgreSQL 17.
 */
public final class PostgresTestImage {
    public static final String PROPERTY = "skillhub.test.postgres.image";
    public static final String DEFAULT = "postgres:16-alpine";

    private static final Pattern MAJOR_TAG = Pattern.compile(":(\\d+)(?:[.\\-][^/]*)?$");

    private PostgresTestImage() {}

    public static String name() {
        String configured = System.getProperty(PROPERTY);
        return configured == null || configured.isBlank() ? DEFAULT : configured.trim();
    }

    /** Major version encoded in the image tag, for example 17 for {@code postgres:17-alpine}. */
    public static OptionalInt expectedMajor() {
        Matcher matcher = MAJOR_TAG.matcher(name());
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }
}
