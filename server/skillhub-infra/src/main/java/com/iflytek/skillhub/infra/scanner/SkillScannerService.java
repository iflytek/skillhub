package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.http.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
        this.baseUrl = baseUrl;
        this.scanPath = scanPath;
        this.healthPath = healthPath;
    }

    public SkillScannerApiResponse scanDirectory(String skillDirectory) {
        String uri = baseUrl + "/scan";
        log.info("Scanning local directory via scanner: {} -> {}", skillDirectory, uri);

        Map<String, Object> body = Map.of("skill_directory", skillDirectory);

        try {
            SkillScannerApiResponse response = httpClient.post(uri, body, SkillScannerApiResponse.class);
            log.info("Scan completed: scanId={}, isSafe={}, findings={}",
                    response.scanId(), response.isSafe(), response.findingsCount());
            return response;
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, body={}", e.getStatusCode(), e.getResponseBody());
            throw e;
        }
    }

    public SkillScannerApiResponse scanUpload(Path skillPackagePath) {
        String uri = baseUrl + scanPath;
        log.info("Uploading skill package to scanner: {}", uri);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(skillPackagePath));

        try {
            SkillScannerApiResponse response = httpClient.postMultipart(uri, parts, SkillScannerApiResponse.class);
            log.info("Scan completed: scanId={}, isSafe={}, findings={}",
                    response.scanId(), response.isSafe(), response.findingsCount());
            return response;
        } catch (HttpClientException e) {
            log.error("Scanner API error: status={}, body={}", e.getStatusCode(), e.getResponseBody());
            throw e;
        }
    }

    public boolean isHealthy() {
        return httpClient.isHealthy(baseUrl + healthPath);
    }
}
