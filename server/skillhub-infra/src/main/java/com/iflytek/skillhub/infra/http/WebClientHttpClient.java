package com.iflytek.skillhub.infra.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

public class WebClientHttpClient implements HttpClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientHttpClient.class);

    private final WebClient webClient;

    public WebClientHttpClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public <T> T get(String uri, Class<T> responseType) {
        log.debug("GET {}", uri);
        try {
            return webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw new HttpClientException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new HttpClientException("GET " + uri + " failed", e);
        }
    }

    @Override
    public <T> T post(String uri, Object body, Class<T> responseType) {
        log.debug("POST {}", uri);
        try {
            return webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw new HttpClientException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new HttpClientException("POST " + uri + " failed", e);
        }
    }

    @Override
    public <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType) {
        return postMultipart(uri, parts, new HttpHeaders(), responseType);
    }

    @Override
    public <T> T postMultipart(String uri,
                               MultiValueMap<String, Object> parts,
                               HttpHeaders headers,
                               Class<T> responseType) {
        log.debug("POST multipart {}", uri);
        try {
            return webClient.post()
                    .uri(uri)
                    .headers(httpHeaders -> httpHeaders.addAll(headers))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            throw new HttpClientException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new HttpClientException("POST multipart " + uri + " failed", e);
        }
    }

    @Override
    public boolean isHealthy(String healthUri) {
        try {
            webClient.get()
                    .uri(healthUri)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", healthUri, e.getMessage());
            return false;
        }
    }
}
