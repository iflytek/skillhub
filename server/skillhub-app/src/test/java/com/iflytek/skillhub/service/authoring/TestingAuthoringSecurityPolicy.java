package com.iflytek.skillhub.service.authoring;

import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;

/**
 * Ready-made policies for unit tests that build components by hand instead of
 * through the Spring context.
 */
public final class TestingAuthoringSecurityPolicy {

    private TestingAuthoringSecurityPolicy() {
    }

    /** Allows every endpoint, stdio, and env ref — for tests focused elsewhere. */
    public static AuthoringSecurityPolicy permissive() {
        return new AuthoringSecurityPolicy() {
            @Override
            public String endpointRejection(String url, EndpointUse use) {
                return null;
            }

            @Override
            public boolean stdioTransportAllowed() {
                return true;
            }

            @Override
            public boolean envRefAllowed(String name) {
                return true;
            }
        };
    }

    /** Production posture: rejects every endpoint, stdio, and env ref. */
    public static AuthoringSecurityPolicy denyAll() {
        return new AuthoringSecurityPolicy() {
            @Override
            public String endpointRejection(String url, EndpointUse use) {
                return "blocked by test policy";
            }

            @Override
            public boolean stdioTransportAllowed() {
                return false;
            }

            @Override
            public boolean envRefAllowed(String name) {
                return false;
            }
        };
    }
}
