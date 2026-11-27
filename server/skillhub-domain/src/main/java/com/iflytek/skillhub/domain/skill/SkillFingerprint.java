package com.iflytek.skillhub.domain.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Content identity shared by individual and repository installation plans. */
public final class SkillFingerprint {
    private SkillFingerprint() {}

    public record FileHash(String path, String sha256) {}

    public static String compute(List<FileHash> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.stream().sorted(Comparator.comparing(FileHash::path)).forEach(file ->
                    digest.update((file.path() + ":" + file.sha256() + "\n").getBytes(StandardCharsets.UTF_8)));
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
