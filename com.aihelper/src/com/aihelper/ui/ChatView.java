package com.aihelper.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;

import com.aihelper.ai.AiChatService;
import com.aihelper.ai.DeepSeekChatService;
import com.aihelper.ai.GeminiChatService;
import com.aihelper.ai.OllamaChatService;
import com.aihelper.ai.OpenAiChatService;
import com.aihelper.ai.QwenChatService;
import com.aihelper.model.ChatMessage;
import com.aihelper.preferences.CredentialsService;
import com.aihelper.preferences.PreferenceConstants;
import com.aihelper.workspace.WorkspaceService;

public class ChatView extends ViewPart {

    public static final String ID = "com.aihelper.ui.chatView";
    private static final int MAX_HISTORY_MESSAGES = 50;

    private StyledText chatArea;
    private Text input;
    private Combo providerCombo;
    private Combo modelCombo;
    private Text statusText;
    private Button sendButton;

    private AiChatService aiService;
    private final WorkspaceService workspaceService = new WorkspaceService();

    private final List<ChatMessage> chatHistory = new ArrayList<>();

    private Font monoFont;
    private Color codeBackground;
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    // ===============================
    // UI
    // ===============================
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Display display = Display.getDefault();
        monoFont = new Font(display, "Consolas", 10, SWT.NORMAL);
        codeBackground = display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);

        createToolbar(parent);
        createChatArea(parent);
        createInput(parent);
        createStatusBar(parent);

        initProvider();
    }

    private void createToolbar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayout(new GridLayout(5, false));
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        providerCombo = new Combo(bar, SWT.READ_ONLY);
        providerCombo.setItems("Ollama", "OpenAI", "Gemini", "Qwen", "DeepSeek");
        providerCombo.select(0);
        providerCombo.addListener(SWT.Selection, e -> switchProvider());

        modelCombo = new Combo(bar, SWT.READ_ONLY);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelCombo.addListener(SWT.Selection, e -> {
            aiService.setModel(modelCombo.getText());
            statusInfo("Modelo activo: " + modelCombo.getText());
        });

        Button clear = new Button(bar, SWT.PUSH);
        clear.setText("Clear");
        clear.addListener(SWT.Selection, e -> {
            chatArea.setText("");
            chatHistory.clear();
            statusInfo("Historial limpiado");
        });

        Button preferencesButton = new Button(bar, SWT.PUSH);
        preferencesButton.setText("\u2699");
        preferencesButton.setToolTipText("Configurar credenciales y endpoints");
        preferencesButton.addListener(SWT.Selection, e -> openPreferences());

    }

    private void createChatArea(Composite parent) {
        chatArea = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        chatArea.setEditable(false);
        chatArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    private void createInput(Composite parent) {
    	Composite inputBar = new Composite(parent, SWT.NONE);
        inputBar.setLayout(new GridLayout(2, false));
        inputBar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        input = new Text(
            inputBar,
            SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL
        );

        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);

        int lineHeight = input.getLineHeight();
        inputData.heightHint = lineHeight * 5 + 6;

        input.setLayoutData(inputData);

        input.addListener(SWT.KeyDown, e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
                if ((e.stateMask & SWT.CTRL) != 0) {
                    e.doit = false;
                    send();
                }
            }
        });

        sendButton = new Button(inputBar, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));
        sendButton.addListener(SWT.Selection, e -> send());
    }

    private void createStatusBar(Composite parent) {
        statusText = new Text(parent, SWT.BORDER | SWT.READ_ONLY);
        statusText.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        statusText.setText("[INFO] Listo");
    }

    // ===============================
    // PROVIDER / MODELS
    // ===============================
    private void initProvider() {
        switchProvider();
    }

    private void switchProvider() {
        configureProvider(providerCombo.getText(), true);
    }

    private void configureProvider(String provider, boolean announce) {
        if (provider == null || provider.isBlank()) {
            return;
        }

        try {
            aiService = newServiceFor(provider);
            loadModels();
            if (announce) {
                appendSystem("Proveedor activo: " + provider);
            }
            warnIfMissingCredentials(provider);
        } catch (Exception e) {
            statusError("Error inicializando proveedor: " + e.getMessage());
        }
    }

    private AiChatService newServiceFor(String provider) {
        return switch (provider) {
            case "OpenAI" -> new OpenAiChatService();
            case "Gemini" -> new GeminiChatService();
            case "Qwen" -> new QwenChatService();
            case "DeepSeek" -> new DeepSeekChatService();
            default -> new OllamaChatService();
        };
    }

    private void openPreferences() {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                getSite().getShell(),
                PreferenceConstants.PAGE_ID,
                null,
                null);

        if (dialog == null) {
            statusError("No se pudo abrir las preferencias");
            return;
        }

        int result = dialog.open();
        if (result == Window.OK) {
            configureProvider(providerCombo.getText(), false);
            statusInfo("Preferencias actualizadas");
        }
    }

    private void warnIfMissingCredentials(String provider) {
        boolean missing = switch (provider) {
            case "OpenAI" -> !CredentialsService.hasOpenAiKey();
            case "Gemini" -> !CredentialsService.hasGeminiKey();
            case "Qwen" -> !CredentialsService.hasQwenKey();
            case "DeepSeek" -> !CredentialsService.hasDeepSeekKey();
            default -> false;
        };

        if (missing) {
            String envVar = providerEnvVar(provider);
            statusError("Configura las credenciales de " + provider +
                (envVar.isEmpty() ? "" : " (" + envVar + ")") +
                " mediante variables de entorno");
        }
    }

    private String providerEnvVar(String provider) {
        return switch (provider) {
            case "OpenAI" -> "OPENAI_API_KEY";
            case "Gemini" -> "GEMINI_API_KEY";
            case "Qwen" -> "QWEN_API_KEY";
            case "DeepSeek" -> "DEEPSEEK_API_KEY";
            default -> "";
        };
    }

    private void loadModels() {
        statusInfo("Cargando modelos...");
        Display display = Display.getDefault();
        AiChatService currentService = aiService;

        CompletableFuture
            .supplyAsync(currentService::listModels)
            .thenAccept(models -> display.asyncExec(() -> {
                if (aiService != currentService) return;
                populateModels(models);
            }))
            .exceptionally(ex -> {
                String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                display.asyncExec(() -> statusError("Error cargando modelos: " + message));
                return null;
            });
    }

    private void populateModels(List<String> models) {
        if (modelCombo.isDisposed()) return;

        if (models == null || models.isEmpty()) {
            modelCombo.setItems(new String[0]);
            statusError("El proveedor no devolvió modelos disponibles");
            return;
        }

        modelCombo.setItems(models.toArray(String[]::new));
        modelCombo.select(0);
        aiService.setModel(models.get(0));
        statusInfo("Modelo cargado: " + models.get(0));
    }

    // ===============================
    // CHAT
    // ===============================
    private void send() {
        String rawMessage = input.getText();
        String message = rawMessage.trim();
        if (message.isEmpty()) return;

        String providerMessage = requiresAsciiOnly()
                ? sanitizeForOllama(message).trim()
                : message;

        if (providerMessage.isEmpty()) {
            statusError("El mensaje contiene caracteres no soportados para este proveedor");
            return;
        }

        input.setText("");
        appendUser(message);

        chatHistory.add(new ChatMessage("user", message));
        trimHistory();

        appendAIHeader();
        statusInfo("Armando contexto...");

        String context = buildContext();
        if (requiresAsciiOnly()) {
            context = sanitizeForOllama(context);
        }
        context = limitContext(context);
        StringBuilder buffer = new StringBuilder();
        Display display = Display.getDefault();

        statusInfo("Enviando mensaje...");
        aiService.sendMessageStreaming(
            providerMessage,
            context,
            chunk -> display.asyncExec(() -> buffer.append(chunk)),
            err -> display.asyncExec(() -> {
                appendSystem("ERROR: " + err.getMessage());
                statusError(err.getMessage());
            }),
            () -> display.asyncExec(() -> {
                String text = buffer.toString();
                handleActions(text);
                appendFormatted(text);
                chatHistory.add(new ChatMessage("assistant", text));
                trimHistory();
                statusInfo("Respuesta completada");
            })
        );
    }

    // ===============================
    // FORMATO TEXTO / CÓDIGO
    // ===============================
    private void appendFormatted(String text) {
        if (text == null || text.isEmpty()) return;

        Pattern p = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```");
        Matcher m = p.matcher(text);

        int last = 0;
        while (m.find()) {
            appendPlain(text.substring(last, m.start()));
            appendCode(m.group(2));
            last = m.end();
        }
        appendPlain(text.substring(last));
        chatArea.append("\n\n");
    }

    private void appendPlain(String txt) {
        if (!txt.isEmpty()) {
            markdownRenderer.append(chatArea, txt);
        }
    }

    private void appendCode(String code) {
        int start = chatArea.getCharCount();
        chatArea.append(code + "\n");

        StyleRange s = new StyleRange();
        s.start = start;
        s.length = code.length();
        s.font = monoFont;
        s.background = codeBackground;
        chatArea.setStyleRange(s);
    }

    // ===============================
    // ACTIONS
    // ===============================
    private void handleActions(String text) {
        handleReadFile(text);
        handleReadProject(text);
    }

    private void handleReadFile(String text) {
        Matcher m = Pattern.compile(
            "\\[ACTION:READ_FILE\\]\\s*project=(\\S+)\\s*path=(\\S+)")
            .matcher(text);
        if (!m.find()) return;

        appendSystem("Contenido solicitado:\n" +
            workspaceService.readFile(m.group(1), m.group(2)));
    }

    private void handleReadProject(String text) {
        if (!text.contains("[ACTION:READ_PROJECT]")) return;
        appendSystem("Snapshot del proyecto:\n" +
            workspaceService.readWorkspaceSnapshot());
    }

    // ===============================
    // CONTEXTO IA (NO TOCADO)
    // ===============================
    private String buildContext() {
        return """
                HISTORIAL DE CHAT:
                %s

                Archivo activo: %s
                Lenguaje: %s

                Código:
                %s

                INSTRUCCIONES:
                ...
                Fin del contexto.
                """.formatted(
            formatHistory(),
            workspaceService.getActiveEditorFileName(),
            workspaceService.getActiveEditorFileExtension(),
            workspaceService.getActiveEditorContent()
        );
    }

    private String formatHistory() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : chatHistory) {
            sb.append(m.getRole().toUpperCase())
              .append(":\n")
              .append(m.getContent())
              .append("\n\n");
        }
        return sb.toString();
    }

    private void trimHistory() {
        while (chatHistory.size() > MAX_HISTORY_MESSAGES) {
            chatHistory.remove(0);
        }
    }

    // ===============================
    // HELPERS
    // ===============================
    private void appendUser(String msg) {
        int start = chatArea.getCharCount();
        chatArea.append("You:\n" + msg + "\n\n");

        StyleRange s = new StyleRange();
        s.start = start;
        s.length = 4;
        s.fontStyle = SWT.BOLD;
        chatArea.setStyleRange(s);
    }

    private void appendAIHeader() {
        chatArea.append("AI:\n");
    }

    private void appendSystem(String msg) {
        chatArea.append("[SYSTEM] " + msg + "\n\n");
    }

    private void statusInfo(String msg) {
        updateStatus("INFO", msg);
    }

    private void statusError(String msg) {
        updateStatus("ERROR", msg);
    }

    private void updateStatus(String level, String msg) {
        Display.getDefault().asyncExec(() -> {
            if (!statusText.isDisposed()) {
                statusText.setText("[" + level + "] " + msg);
            }
        });
    }
    
    private boolean requiresAsciiOnly() {
        return aiService instanceof OllamaChatService;
    }

    private String sanitizeForOllama(String text) {
        if (text == null) return "";

        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "")
            .replaceAll("[^\\x00-\\x7F]", "")
            .trim();
    }
    
    private String limitContext(String context) {
        int MAX_CHARS = 6000; // Ollama safe
        if (context.length() <= MAX_CHARS) return context;
        return context.substring(context.length() - MAX_CHARS);
    }

    @Override
    public void setFocus() {
        input.setFocus();
    }

    @Override
    public void dispose() {
        if (monoFont != null) monoFont.dispose();
        super.dispose();
    }
}
