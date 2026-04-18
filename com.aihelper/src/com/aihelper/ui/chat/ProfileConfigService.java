package com.aihelper.ui.chat;

import org.eclipse.jface.preference.IPreferenceStore;

import com.aihelper.preferences.CredentialsService;
import com.aihelper.preferences.PreferenceConstants;

public class ProfileConfigService {

    private final IPreferenceStore store;

    public ProfileConfigService() {
        this.store = CredentialsService.preferenceStore();
    }

    public String getProvider(ChatProfile profile) {
        return switch (profile) {
            case DEV_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_DEV_PROVIDER));
            case ARQ_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_ARQ_PROVIDER));
            case AUDITOR_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_AUDIT_PROVIDER));
            default -> null;
        };
    }

    public String getModel(ChatProfile profile) {
        return switch (profile) {
            case DEV_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_DEV_MODEL));
            case ARQ_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_ARQ_MODEL));
            case AUDITOR_SENIOR -> trimToNull(store.getString(PreferenceConstants.TEAM_AUDIT_MODEL));
            default -> null;
        };
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
