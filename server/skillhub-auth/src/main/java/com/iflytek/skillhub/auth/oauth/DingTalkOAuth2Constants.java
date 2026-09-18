package com.iflytek.skillhub.auth.oauth;

/** Shared protocol constants for the DingTalk OAuth2 adapter. */
public final class DingTalkOAuth2Constants {

    public static final String REGISTRATION_ID = "dingtalk";
    public static final String AUTHORIZATION_SCOPE = "openid";
    public static final String ACCESS_TOKEN_HEADER = "x-acs-dingtalk-access-token";

    /**
     * The only accepted subject claim. DingTalk also returns {@code openId} and {@code userId}, but
     * they must not act as fallbacks: {@code openId} is scoped per app and {@code userId} per
     * organization, so a login that fell back to either would bind a different identity than a
     * later login carrying {@code unionId}, splitting one person across two platform accounts.
     * Promoting another claim later needs an explicit alias migration.
     */
    static final String SUBJECT_CLAIM_NAME = "unionId";

    public static final String SUBJECT_ATTRIBUTE = "dingtalkSubject";

    private DingTalkOAuth2Constants() {
    }
}
