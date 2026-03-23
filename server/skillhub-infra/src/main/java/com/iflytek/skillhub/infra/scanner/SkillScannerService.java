package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.http.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

public class SkillScannerService {

    private static final Logger log = LoggerFactory.getLogger(SkillScannerService.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String scanPath;
    private final String healthPath;

    public SkillScannerService(HttpClient httpClient,
                               String baseUrl,
                               String scanPath,
                               String healthPath) {
        this.httpClient = httpClient;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.scanPath = scanPath;
        this.healthPath = healthPath;
    }

    public SkillScannerApiResponse scanDirectory(String skillDirectory, ScanOptions options) {
        String uri = baseUrl + "/scan";
        log.info("Scanning local directory via scanner: {} -> {}", skillDirectory, uri);

        Map<String, Object> body = buildScanRequestBody(skillDirectory, options);
        try {
            return httpClient.post(uri, body, SkillScannerApiResponse.class);
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, body={}", e.getStatusCode(), summarizeResponseBody(e.getResponseBody()));
            throw e;
        }
    }

    public SkillScannerApiResponse scanUpload(Path skillPackagePath, ScanOptions options) {
        String uri = buildUploadUri(options);
        log.info("Uploading skill package to scanner: {}", sanitizeUri(uri));

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(skillPackagePath));
        HttpHeaders headers = buildScannerHeaders(options);
        try {
            return httpClient.postMultipart(uri, parts, headers, SkillScannerApiResponse.class);
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, body={}", e.getStatusCode(), summarizeResponseBody(e.getResponseBody()));
            throw e;
        }
    }

    public boolean isHealthy() {
        return httpClient.isHealthy(baseUrl + healthPath);
    }

    private Map<String, Object> buildScanRequestBody(String skillDirectory, ScanOptions options) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("skill_directory", skillDirectory);
        body.put("use_behavioral", options.useBehavioral());
        body.put("use_llm", options.useLlm());
        body.put("llm_provider", options.llmProvider());
        body.put("enable_meta", options.enableMeta());
        body.put("use_aidefense", options.useAidefense());
        if (options.useAidefense() && !options.aidefenseApiKey().isEmpty()) {
            body.put("aidefense_api_key", options.aidefenseApiKey());
        }
        body.put("use_virustotal", options.useVirusTotal());
        body.put("use_trigger", options.useTrigger());
        return body;
    }

    private String buildUploadUri(ScanOptions options) {
        StringBuilder uri = new StringBuilder(baseUrl + scanPath);
        uri.append("?use_behavioral=").append(options.useBehavioral());
        uri.append("&use_llm=").append(options.useLlm());
        uri.append("&llm_provider=").append(options.llmProvider());
        uri.append("&enable_meta=").append(options.enableMeta());
        uri.append("&use_aidefense=").append(options.useAidefense());
        uri.append("&use_virustotal=").append(options.useVirusTotal());
        uri.append("&use_trigger=").append(options.useTrigger());
        return uri.toString();
    }

    private HttpHeaders buildScannerHeaders(ScanOptions options) {
        HttpHeaders headers = new HttpHeaders();
        if (options.useAidefense() && !options.aidefenseApiKey().isEmpty()) {
            headers.add("X-AIDefense-Api-Key", options.aidefenseApiKey());
        }
        return headers;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        URI uri = URI.create(rawBaseUrl);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Scanner base URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Scanner base URL must include a host");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Scanner base URL must not include user info, query, or fragment");
        }
        String normalized = uri.toString();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String summarizeResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String singleLine = body.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) + "...[truncated]" : singleLine;
    }

    private String sanitizeUri(String uri) {
        return uri.replaceAll("([?&]aidefense_api_key=)[^&]+", "$1***");
    }
}
