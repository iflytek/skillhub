/**
 * OAuth login adapters and flow coordination for browser-based third-party
 * authentication.
 *
 * <p>{@code OAuthLoginFlowService} is the package-level workflow owner. It
 * centralizes provider claim loading, access-policy evaluation, account
 * provisioning, return-target persistence, and redirect resolution, while the
 * surrounding handlers and resolvers remain transport adapters.
 */
package com.iflytek.skillhub.auth.oauth;
