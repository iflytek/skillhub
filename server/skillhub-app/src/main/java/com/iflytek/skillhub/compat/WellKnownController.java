package com.iflytek.skillhub.compat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WellKnownController {

    @GetMapping("/.well-known/clawhub.json")
    public Map<String, String> clawhubConfig() {
        return Map.of("apiBase", "/api/v1");
    }
}
