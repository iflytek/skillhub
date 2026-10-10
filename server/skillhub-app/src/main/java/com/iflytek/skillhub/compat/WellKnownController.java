package com.iflytek.skillhub.compat;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * Serves well-known compatibility metadata used by external clients to discover the API base.
 */
@RestController
public class WellKnownController {

    @GetMapping("/.well-known/clawhub.json")
    public Map<String, Object> clawhubConfig(HttpServletRequest request) {
        // Honor the deployment sub-path so CLI clients discover /<prefix>/api/v1 instead of
        // the domain-root /api/v1. With forward-headers-strategy=framework, an upstream
        // X-Forwarded-Prefix is reflected into the request context path.
        String contextPath = request.getContextPath();
        String prefix = (contextPath == null) ? "" : contextPath;
        return Map.of(
                "apiBase", prefix + "/api/v1",
                "capabilities", List.of("skill-suite-v1")
        );
    }
}
