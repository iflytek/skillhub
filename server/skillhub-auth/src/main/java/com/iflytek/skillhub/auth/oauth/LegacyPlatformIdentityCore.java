package com.iflytek.skillhub.auth.oauth;

/** Compatibility seam that lets public OAuth/OIDC exercise the unified identity core. */
@FunctionalInterface
public interface LegacyPlatformIdentityCore {

    LegacyPlatformIdentityDecision evaluate(OAuthClaims claims);
}
