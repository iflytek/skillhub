package com.iflytek.skillhub.domain.authoring.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Deployment-owned ceiling; a draft may only select a subset, never grant access. */
public final class ScriptEnvironmentPolicy {

    private static final Pattern NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Set<String> RESERVED = Set.of(
            "PATH", "HOME", "LANG", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP");
    private final Supplier<List<String>> deploymentAllowlist;

    public ScriptEnvironmentPolicy(Supplier<List<String>> deploymentAllowlist) {
        this.deploymentAllowlist = java.util.Objects.requireNonNull(deploymentAllowlist);
        allowedNames();
    }

    /** Reads the currently effective deployment policy on every call, including execution. */
    public Set<String> authorize(Object requested) {
        Set<String> allowed = allowedNames();
        if (requested == null) {
            return Set.of();
        }
        if (!(requested instanceof List<?> entries)) {
            throw new IllegalArgumentException("envAllowlist must be a list of variable names");
        }
        Set<String> selected = new LinkedHashSet<>();
        for (Object entry : entries) {
            if (!(entry instanceof String name) || !validName(name) || !allowed.contains(name)) {
                throw new IllegalArgumentException(
                        "envAllowlist contains a variable not permitted by deployment policy");
            }
            selected.add(name);
        }
        return java.util.Collections.unmodifiableSet(selected);
    }

    private Set<String> allowedNames() {
        List<String> configured = deploymentAllowlist.get();
        if (configured == null) {
            return Set.of();
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String name : configured) {
            if (name == null || !validName(name)) {
                throw new IllegalStateException("Invalid local-script deployment environment allowlist");
            }
            allowed.add(name);
        }
        return Set.copyOf(allowed);
    }

    private static boolean validName(String name) {
        return NAME.matcher(name).matches() && !RESERVED.contains(name.toUpperCase(Locale.ROOT));
    }
}
