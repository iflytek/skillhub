package com.iflytek.skillhub.search;

public interface SearchEmbeddingService {
    String embed(String text);

    double similarity(String text, String serializedVector);
}
