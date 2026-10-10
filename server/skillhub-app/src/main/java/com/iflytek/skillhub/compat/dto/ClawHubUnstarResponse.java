package com.iflytek.skillhub.compat.dto;

public record ClawHubUnstarResponse(
    boolean ok,
    boolean unstarred,
    boolean alreadyUnstarred
) {
    public ClawHubUnstarResponse(boolean unstarred, boolean alreadyUnstarred) {
        this(true, unstarred, alreadyUnstarred);
    }
}
