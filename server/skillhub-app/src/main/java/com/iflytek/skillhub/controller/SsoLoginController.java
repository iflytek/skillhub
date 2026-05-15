package com.iflytek.skillhub.controller;

import java.io.IOException;

import com.iflytek.skillhub.auth.config.SsoProperties;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.sso.SsoClient;
import com.iflytek.skillhub.auth.sso.SsoIdentityService;
import com.iflytek.skillhub.auth.sso.SsoUser;
import com.iflytek.skillhub.auth.sso.TicketValidationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * CAS-based SSO login controller.
 *
 * <p>Handles the redirect to the SSO server and the ticket-callback exchange
 * that establishes a platform session on success.
 */
@Controller
@RequestMapping("/api/v1/auth/sso")
public class SsoLoginController {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginController.class);

    private final SsoProperties properties;
    private final SsoClient ssoClient;
    private final SsoIdentityService ssoIdentityService;
    private final PlatformSessionService platformSessionService;

    public SsoLoginController(SsoProperties properties,
                              SsoClient ssoClient,
                              SsoIdentityService ssoIdentityService,
                              PlatformSessionService platformSessionService) {
        this.properties = properties;
        this.ssoClient = ssoClient;
        this.ssoIdentityService = ssoIdentityService;
        this.platformSessionService = platformSessionService;
    }

    /**
     * Initiates SSO login by redirecting the browser to the SSO login page.
     */
    @GetMapping("/login")
    public void ssoLogin(@RequestParam(value = "returnTo", required = false) String returnTo,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (!properties.isEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "SSO login is disabled");
            return;
        }
        if (returnTo != null) {
            request.getSession().setAttribute("ssoReturnTo", returnTo);
        }
        String ssoLoginUrl = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path("/login")
                .queryParam("clientUrl", properties.getClientUrl())
                .build()
                .toUriString();
        response.sendRedirect(ssoLoginUrl);
    }

    /**
     * Receives the CAS ticket callback from the SSO server, validates the
     * ticket, establishes a platform session, and redirects the browser to the
     * frontend home page.
     */
    @GetMapping("/callback")
    public void ssoCallback(@RequestParam("ticket") String ticket,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        if (!properties.isEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "SSO login is disabled");
            return;
        }

        try {
            SsoUser ssoUser = ssoClient.validateTicket(ticket);
            var principal = ssoIdentityService.resolveOrCreate(ssoUser);
            platformSessionService.establishSession(principal, request);
            String returnTo = (String) request.getSession().getAttribute("ssoReturnTo");
            if (returnTo != null) {
                request.getSession().removeAttribute("ssoReturnTo");
            }
            response.sendRedirect(returnTo != null ? returnTo : "/");
        } catch (TicketValidationException e) {
            log.warn("SSO ticket validation failed: {}", e.getMessage());
            response.sendRedirect("/login?error=sso_auth_failed");
        } catch (Exception e) {
            log.error("SSO callback error", e);
            response.sendRedirect("/login?error=sso_error");
        }
    }
}
