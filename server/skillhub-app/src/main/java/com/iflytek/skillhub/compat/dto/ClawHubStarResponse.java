package com.iflytek.skillhub.compat.dto;

public record ClawHubStarResponse(
    boolean ok,
    boolean starred,
    boolean alreadyStarred
) {
    public ClawHubStarResponse(boolean starred, boolean alreadyStarred) {
        this(true, starred, alreadyStarred);
    }
}
