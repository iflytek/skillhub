package com.iflytek.skillhub.infra.http;

public class HttpClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public HttpClientException(int statusCode, String responseBody) {
        super(responseBody == null || responseBody.isBlank()
                ? "HTTP " + statusCode
                : "HTTP " + statusCode + ": " + bounded(responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? null : bounded(responseBody);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message + ": " + rootCauseSummary(cause), cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    private static String rootCauseSummary(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String bounded(String body) {
        return body.length() <= 2048 ? body : body.substring(0, 2048) + "...[truncated]";
    }
}
