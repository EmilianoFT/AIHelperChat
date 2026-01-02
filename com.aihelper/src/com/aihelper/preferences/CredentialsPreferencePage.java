package com.aihelper.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Preference page to capture API keys, default models, and base URLs for
 * every supported provider.
 */
public class CredentialsPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public CredentialsPreferencePage() {
        super(GRID);
        setTitle("AI Helper Chat");
        setDescription("Configura las credenciales y endpoints de los proveedores de IA.");
        setPreferenceStore(CredentialsService.preferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {
        // nothing to init
    }

    @Override
    protected void createFieldEditors() {
        addField(new StringFieldEditor(
                PreferenceConstants.OPENAI_API_KEY,
                "OpenAI API Key",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.OPENAI_DEFAULT_MODEL,
                "OpenAI modelo por defecto",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.OPENAI_BASE_URL,
                "OpenAI base URL",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.GEMINI_API_KEY,
                "Gemini API Key",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.GEMINI_DEFAULT_MODEL,
                "Gemini modelo por defecto",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.GEMINI_BASE_URL,
                "Gemini base URL",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.QWEN_API_KEY,
                "Qwen API Key",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.QWEN_DEFAULT_MODEL,
                "Qwen modelo por defecto",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.QWEN_BASE_URL,
                "Qwen base URL",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.DEEPSEEK_API_KEY,
                "DeepSeek API Key",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.DEEPSEEK_DEFAULT_MODEL,
                "DeepSeek modelo por defecto",
                getFieldEditorParent()));
        addField(new StringFieldEditor(
                PreferenceConstants.DEEPSEEK_BASE_URL,
                "DeepSeek base URL",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.OLLAMA_BASE_URL,
                "Ollama base URL",
                getFieldEditorParent()));
    }
}
