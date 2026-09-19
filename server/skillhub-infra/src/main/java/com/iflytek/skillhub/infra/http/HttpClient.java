package com.iflytek.skillhub.infra.http;

import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

public interface HttpClient {

    <T> T get(String uri, Class<T> responseType);

    default <T> T post(String uri, Object body, Class<T> responseType) {
        return post(uri, body, new HttpHeaders(), responseType);
    }

    <T> T post(String uri, Object body, HttpHeaders headers, Class<T> responseType);

    <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType);

    <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, HttpHeaders headers, Class<T> responseType);

    boolean isHealthy(String healthUri);
}
