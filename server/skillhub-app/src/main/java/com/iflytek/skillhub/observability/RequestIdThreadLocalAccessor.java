package com.iflytek.skillhub.observability;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * Captures and restores the authoritative Request ID scope for asynchronous execution.
 */
public final class RequestIdThreadLocalAccessor implements ThreadLocalAccessor<String> {

    public static final String KEY = "skillhub.request-id";

    private final RequestIdAccessor requestIdAccessor;

    public RequestIdThreadLocalAccessor(RequestIdAccessor requestIdAccessor) {
        this.requestIdAccessor = requestIdAccessor;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return requestIdAccessor.current();
    }

    @Override
    public void setValue(String value) {
        requestIdAccessor.replace(value);
    }

    @Override
    public void setValue() {
        requestIdAccessor.replace(null);
    }
}
