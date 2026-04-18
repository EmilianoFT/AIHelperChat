package com.aihelper.ui.chat;

public enum ChatProfile {
    DEV_SENIOR("Dev Senior"),
    ARQ_SENIOR("Arquitecto Senior"),
    AUDITOR_SENIOR("Auditor Senior"),
    TEAM_LEADER("Team Leader");

    private final String displayName;

    ChatProfile(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
