package com.iflytek.skillhub.infra.http;

import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

public interface HttpClient {

    <T> T get(String uri, Class<T> responseType);

    <T> T post(String uri, Object body, Class<T> responseType);

    <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType);

    <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, HttpHeaders headers, Class<T> responseType);

    boolean isHealthy(String healthUri);
}
