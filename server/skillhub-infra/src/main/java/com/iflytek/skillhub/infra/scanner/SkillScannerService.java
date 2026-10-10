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
            return httpClient.post(uri, body, buildScannerHeaders(options), SkillScannerApiResponse.class);
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, operation=scanDirectory", e.getStatusCode());
            throw sanitizedException(e);
        }
    }

    public SkillScannerApiResponse scanUpload(Path skillPackagePath, ScanOptions options) {
        String uri = buildUploadUri(options);
        log.info("Uploading skill package to scanner: {}", uri);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(skillPackagePath));
        addScannerOptionsParts(parts, options);
        HttpHeaders headers = buildScannerHeaders(options);
        try {
            return httpClient.postMultipart(uri, parts, headers, SkillScannerApiResponse.class);
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, operation=scanUpload", e.getStatusCode());
            throw sanitizedException(e);
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
        body.put("llm_consensus_runs", options.llmConsensusRuns());
        body.put("policy", options.policyPreset());
        body.put("enable_meta", options.enableMeta());
        body.put("use_aidefense", options.useAidefense());
        body.put("use_virustotal", options.useVirusTotal());
        body.put("use_trigger", options.useTrigger());
        return body;
    }

    private void addScannerOptionsParts(MultiValueMap<String, Object> parts, ScanOptions options) {
        parts.add("use_behavioral", Boolean.toString(options.useBehavioral()));
        parts.add("use_llm", Boolean.toString(options.useLlm()));
        parts.add("llm_provider", options.llmProvider());
        parts.add("llm_consensus_runs", Integer.toString(options.llmConsensusRuns()));
        parts.add("policy", options.policyPreset());
        parts.add("enable_meta", Boolean.toString(options.enableMeta()));
        parts.add("use_aidefense", Boolean.toString(options.useAidefense()));
        parts.add("use_virustotal", Boolean.toString(options.useVirusTotal()));
        parts.add("use_trigger", Boolean.toString(options.useTrigger()));
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
            headers.add("X-AIDefense-Key", options.aidefenseApiKey());
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

    private HttpClientException sanitizedException(HttpClientException exception) {
        if (exception.getStatusCode() > 0) {
            return new HttpClientException(exception.getStatusCode(), null);
        }
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return new HttpClientException("Scanner API request failed", cause);
    }
}
