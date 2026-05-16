package com.iflytek.skillhub.service.media;

import com.iflytek.skillhub.domain.media.MediaAssetService;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production {@link MediaAssetService.MediaHasher} that computes lowercase
 * SHA-256 hex digests of the upload body. Used both as the storage path key
 * and for cross-owner deduplication.
 */
@Component
public class Sha256MediaHasher implements MediaAssetService.MediaHasher {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Override
    public String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                out[i * 2]     = HEX[(digest[i] >> 4) & 0xF];
                out[i * 2 + 1] = HEX[digest[i] & 0xF];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
