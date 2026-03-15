package com.iflytek.skillhub.search;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HashingSearchEmbeddingService implements SearchEmbeddingService {
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final int DIMENSIONS = 64;
    private static final double NGRAM_WEIGHT = 0.35D;

    @Override
    public String embed(String text) {
        double[] vector = buildVector(text);
        return Arrays.stream(vector)
                .mapToObj(value -> String.format(Locale.ROOT, "%.6f", value))
                .collect(Collectors.joining(","));
    }

    @Override
    public double similarity(String text, String serializedVector) {
        if (serializedVector == null || serializedVector.isBlank()) {
            return 0D;
        }
        double[] left = buildVector(text);
        double[] right = parseVector(serializedVector);
        if (left.length != right.length || left.length == 0) {
            return 0D;
        }

        double dot = 0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }

    private double[] buildVector(String text) {
        double[] vector = new double[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }

        TOKEN_SPLITTER.splitAsStream(text.toLowerCase(Locale.ROOT))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .forEach(token -> {
                    addTokenWeight(vector, token, 1D + Math.min(token.length(), 12) / 12D);
                    addCharacterNgrams(vector, token);
                });

        normalize(vector);
        return vector;
    }

    private void addTokenWeight(double[] vector, String token, double weight) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, DIMENSIONS);
        vector[index] += weight;
    }

    private void addCharacterNgrams(double[] vector, String token) {
        if (token.length() < 3) {
            return;
        }
        for (int i = 0; i <= token.length() - 3; i++) {
            String trigram = token.substring(i, i + 3);
            addTokenWeight(vector, trigram, NGRAM_WEIGHT);
        }
    }

    private double[] parseVector(String serializedVector) {
        String[] parts = serializedVector.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i]);
        }
        normalize(vector);
        return vector;
    }

    private void normalize(double[] vector) {
        double magnitude = 0D;
        for (double value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0D) {
            return;
        }
        double norm = Math.sqrt(magnitude);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
