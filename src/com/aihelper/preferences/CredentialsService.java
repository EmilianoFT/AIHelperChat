package com.aihelper.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.aihelper.Activator;

public final class CredentialsService {

    private CredentialsService() {}

    public static boolean hasOpenAiKey() {
        return !getOpenAiApiKey().isBlank();
    }

    public static boolean hasGeminiKey() {
        return !getGeminiApiKey().isBlank();
    }

    public static boolean hasQwenKey() {
        return !getQwenApiKey().isBlank();
    }

    public static boolean hasDeepSeekKey() {
        return !getDeepSeekApiKey().isBlank();
    }

    public static String getOpenAiApiKey() {
        return resolve("OPENAI_API_KEY", PreferenceConstants.OPENAI_API_KEY);
    }

    public static String getGeminiApiKey() {
        return resolve("GEMINI_API_KEY", PreferenceConstants.GEMINI_API_KEY);
    }

    public static String getQwenApiKey() {
        return resolve("QWEN_API_KEY", PreferenceConstants.QWEN_API_KEY);
    }

    public static String getDeepSeekApiKey() {
        return resolve("DEEPSEEK_API_KEY", PreferenceConstants.DEEPSEEK_API_KEY);
    }

    public static String getOpenAiDefaultModel() {
        return preferenceStore().getString(PreferenceConstants.OPENAI_DEFAULT_MODEL);
    }

    public static String getGeminiDefaultModel() {
        return preferenceStore().getString(PreferenceConstants.GEMINI_DEFAULT_MODEL);
    }

    public static String getGeminiBaseUrl() {
        return preferenceStore().getString(PreferenceConstants.GEMINI_BASE_URL);
    }

    public static String getQwenDefaultModel() {
        return preferenceStore().getString(PreferenceConstants.QWEN_DEFAULT_MODEL);
    }

    public static String getQwenBaseUrl() {
        return preferenceStore().getString(PreferenceConstants.QWEN_BASE_URL);
    }

    public static String getDeepSeekDefaultModel() {
        return preferenceStore().getString(PreferenceConstants.DEEPSEEK_DEFAULT_MODEL);
    }

    public static String getDeepSeekBaseUrl() {
        return preferenceStore().getString(PreferenceConstants.DEEPSEEK_BASE_URL);
    }
    
    public static String getOpenAiBaseUrl() {
        return preferenceStore().getString(PreferenceConstants.OPENAI_BASE_URL);
    }

    public static String getOllamaBaseUrl() {
        return preferenceStore().getString(PreferenceConstants.OLLAMA_BASE_URL);
    }

    private static String resolve(String envKey, String preferenceKey) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return preferenceStore().getString(preferenceKey);
    }

    public static IPreferenceStore preferenceStore() {
        Activator activator = Activator.getDefault();
        if (activator != null) {
            return activator.getPreferenceStore();
        }
        return new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID);
    }
}
