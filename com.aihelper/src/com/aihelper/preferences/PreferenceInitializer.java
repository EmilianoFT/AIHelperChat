package com.aihelper.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.aihelper.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store;
        Activator activator = Activator.getDefault();
        if (activator != null) {
            store = activator.getPreferenceStore();
        } else {
            store = new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID);
        }

        store.setDefault(PreferenceConstants.OPENAI_API_KEY, "");
        store.setDefault(PreferenceConstants.OPENAI_DEFAULT_MODEL, "gpt-4o-mini");
        store.setDefault(PreferenceConstants.OPENAI_BASE_URL, "https://api.openai.com/v1");


        store.setDefault(PreferenceConstants.GEMINI_API_KEY, "");
        store.setDefault(PreferenceConstants.GEMINI_DEFAULT_MODEL, "gemini-1.5-flash");
        store.setDefault(PreferenceConstants.GEMINI_BASE_URL, "https://generativelanguage.googleapis.com/v1beta");

        store.setDefault(PreferenceConstants.QWEN_API_KEY, "");
        store.setDefault(PreferenceConstants.QWEN_DEFAULT_MODEL, "qwen-plus");
        store.setDefault(PreferenceConstants.QWEN_BASE_URL, "https://dashscope.aliyuncs.com/compatible-mode/v1");

        store.setDefault(PreferenceConstants.DEEPSEEK_API_KEY, "");
        store.setDefault(PreferenceConstants.DEEPSEEK_DEFAULT_MODEL, "deepseek-chat");
        store.setDefault(PreferenceConstants.DEEPSEEK_BASE_URL, "https://api.deepseek.com/beta/v1");

        store.setDefault(PreferenceConstants.OLLAMA_BASE_URL, "http://localhost:11434");

        store.setDefault(PreferenceConstants.CHAT_MAX_HISTORY, 50);
        store.setDefault(PreferenceConstants.LIST_MAX_DEPTH, 5);
        store.setDefault(PreferenceConstants.LIST_MAX_LIMIT, 500);

    }
}
