package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Serves well-known compatibility metadata used by external clients to discover the API base.
 */
@RestController
public class WellKnownController {
    @GetMapping("/.well-known/clawhub.json")
    public Map<String, String> clawhubConfig(HttpServletRequest request) {
        return Map.of("apiBase", OAuthLoginRedirectSupport.apiBase(request));
    }
}
