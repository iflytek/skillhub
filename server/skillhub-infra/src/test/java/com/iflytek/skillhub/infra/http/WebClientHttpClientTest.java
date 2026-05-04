package com.iflytek.skillhub.infra.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@SuppressWarnings({"unchecked", "rawtypes"})
class WebClientHttpClientTest {

    private WebClient webClient;
    private WebClientHttpClient client;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        client = new WebClientHttpClient(webClient);
    }

    @Test
    void get_shouldReturnResponse() {
        WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri("/test")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("ok"));

        String result = client.get("/test", String.class);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void get_shouldThrowHttpClientExceptionOnWebClientResponseException() {
        WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri("/fail")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        WebClientResponseException ex = WebClientResponseException.create(
                500, "Internal Server Error", org.springframework.http.HttpHeaders.EMPTY, null, null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(ex));

        assertThatThrownBy(() -> client.get("/fail", String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("500");
    }

    @Test
    void get_shouldThrowHttpClientExceptionOnGenericException() {
        WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri("/err")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenThrow(new RuntimeException("network"));

        assertThatThrownBy(() -> client.get("/err", String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("GET /err failed");
    }

    @Test
    void post_shouldReturnResponse() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/test")).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(postSpec);
        when(postSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("posted"));

        String result = client.post("/test", "body", String.class);

        assertThat(result).isEqualTo("posted");
    }

    @Test
    void post_shouldThrowHttpClientExceptionOnWebClientResponseException() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/fail")).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(postSpec);
        when(postSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        WebClientResponseException ex = WebClientResponseException.create(
                400, "Bad Request", org.springframework.http.HttpHeaders.EMPTY, null, null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(ex));

        assertThatThrownBy(() -> client.post("/fail", "body", String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("400");
    }

    @Test
    void post_shouldThrowHttpClientExceptionOnGenericException() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/err")).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(postSpec);
        when(postSpec.bodyValue(any())).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> client.post("/err", "body", String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("POST /err failed");
    }

    @Test
    void postMultipart_withoutHeaders_shouldReturnResponse() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/upload")).thenReturn(postSpec);
        when(postSpec.headers(any())).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(postSpec);
        when(postSpec.body(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("uploaded"));

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        String result = client.postMultipart("/upload", parts, String.class);

        assertThat(result).isEqualTo("uploaded");
    }

    @Test
    void postMultipart_withHeaders_shouldReturnResponse() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/upload")).thenReturn(postSpec);
        when(postSpec.headers(any())).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(postSpec);
        when(postSpec.body(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("uploaded"));

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Auth", "token");
        String result = client.postMultipart("/upload", parts, headers, String.class);

        assertThat(result).isEqualTo("uploaded");
    }

    @Test
    void postMultipart_shouldThrowHttpClientExceptionOnWebClientResponseException() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/fail")).thenReturn(postSpec);
        when(postSpec.headers(any())).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(postSpec);
        when(postSpec.body(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        WebClientResponseException ex = WebClientResponseException.create(
                413, "Payload Too Large", org.springframework.http.HttpHeaders.EMPTY, null, null);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(ex));

        assertThatThrownBy(() -> client.postMultipart("/fail", new LinkedMultiValueMap<>(), String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("413");
    }

    @Test
    void postMultipart_shouldThrowHttpClientExceptionOnGenericException() {
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/err")).thenReturn(postSpec);
        when(postSpec.headers(any())).thenReturn(postSpec);
        when(postSpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(postSpec);
        when(postSpec.body(any())).thenThrow(new RuntimeException("broken"));

        assertThatThrownBy(() -> client.postMultipart("/err", new LinkedMultiValueMap<>(), String.class))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("POST multipart /err failed");
    }

    @Test
    void isHealthy_shouldReturnTrueOnSuccess() {
        WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri("/health")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(mock(org.springframework.http.ResponseEntity.class)));

        assertThat(client.isHealthy("/health")).isTrue();
    }

    @Test
    void isHealthy_shouldReturnFalseOnException() {
        WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri("/health")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenThrow(new RuntimeException("timeout"));

        assertThat(client.isHealthy("/health")).isFalse();
    }
}
