package com.iflytek.skillhub.search;

/**
 * Converts text into a serialized vector form and evaluates similarity against stored vectors.
 */
public interface SearchEmbeddingService {
    String embed(String text);

    double similarity(String text, String serializedVector);
}
