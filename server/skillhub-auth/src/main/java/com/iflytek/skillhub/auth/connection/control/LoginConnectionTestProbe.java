package com.iflytek.skillhub.auth.connection.control;

/** Adapter-supplied remote probe. It must not mutate platform identity state. */
@FunctionalInterface
public interface LoginConnectionTestProbe {

    void verify(LoginConnectionRevisionSnapshot candidate);
}
