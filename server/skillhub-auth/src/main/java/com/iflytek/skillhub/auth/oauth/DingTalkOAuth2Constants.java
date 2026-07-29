package com.iflytek.skillhub.auth.oauth;

import java.util.List;

/** Shared protocol constants for the DingTalk OAuth2 adapter. */
public final class DingTalkOAuth2Constants {

    public static final String REGISTRATION_ID = "dingtalk";
    public static final String AUTHORIZATION_SCOPE = "openid";
    public static final String ACCESS_TOKEN_HEADER = "x-acs-dingtalk-access-token";
    public static final String SUBJECT_ATTRIBUTE = "dingtalkSubject";
    static final List<String> SUBJECT_CLAIM_NAMES = List.of("unionId", "openId", "userId");

    private DingTalkOAuth2Constants() {
    }
}
