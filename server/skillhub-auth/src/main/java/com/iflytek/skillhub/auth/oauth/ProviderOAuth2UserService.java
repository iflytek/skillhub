package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Strategy interface for provider-specific OAuth user loading. Implementations override the
 * default user info loading for providers whose endpoints deviate from the standard
 * flat-attribute response format.
 */
public interface ProviderOAuth2UserService extends OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    String getProvider();
}
