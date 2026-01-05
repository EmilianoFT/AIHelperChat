package com.aihelper.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.dialogs.MessageDialog;
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
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
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
import com.aihelper.ui.ApplyChangesDialog;
import com.aihelper.ui.chat.ChatActionDispatcher;
import com.aihelper.ui.chat.ChatContextBuilder;
import com.aihelper.ui.chat.CodeFenceHelper;
import com.aihelper.ui.chat.ChatHistoryStore;
import com.aihelper.workspace.WorkspaceService;
import com.aihelper.workspace.DiffService;

public class ChatView extends ViewPart {

    public static final String ID = "com.aihelper.ui.chatView";

    private StyledText chatArea;
    private Text input;
    private Combo providerCombo;
    private Combo modelCombo;
    private Text statusText;
    private Text progressText;
    private Button spinnerLabel;
    private Button sendButton;
    private Button stopButton;
    private Button copyCodeButton;
    private Button applyCodeButton;
    private Button errorsButton;

    private AiChatService aiService;
    private final WorkspaceService workspaceService = new WorkspaceService();
    private ChatActionDispatcher actionDispatcher;
    private ChatContextBuilder contextBuilder;
    private final ChatHistoryStore historyStore = new ChatHistoryStore();
    private final DiffService diffService = new DiffService();

    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> errorLog = new ArrayList<>();

    private Font monoFont;
    private Color codeBackground;
    private Color codeBorder;
    private Color userBubble;
    private Color aiBubble;
    private Color systemBubble;
    private Color keywordColor;
    private Color stringColor;
    private Color commentColor;
    private Color userTextColor;
    private Color aiTextColor;
    private Color systemTextColor;
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private String lastCodeBlock = "";
    private Runnable currentCancel = null;
    private int aiMessageStart = -1;

    // ===============================
    // UI
    // ===============================
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Display display = Display.getDefault();
        monoFont = new Font(display, "Consolas", 10, SWT.NORMAL);
        codeBackground = display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
        codeBorder = display.getSystemColor(SWT.COLOR_GRAY);
        userBubble = display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        aiBubble = display.getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT);
        systemBubble = display.getSystemColor(SWT.COLOR_GRAY);
        keywordColor = display.getSystemColor(SWT.COLOR_DARK_BLUE);
        stringColor = display.getSystemColor(SWT.COLOR_DARK_GREEN);
        commentColor = display.getSystemColor(SWT.COLOR_DARK_GRAY);
        userTextColor = display.getSystemColor(SWT.COLOR_BLACK);
        aiTextColor = display.getSystemColor(SWT.COLOR_DARK_BLUE);
        systemTextColor = display.getSystemColor(SWT.COLOR_WHITE);

        createToolbar(parent);
        createChatArea(parent);
        createInput(parent);
        createStatusBar(parent);

        loadHistory();

        contextBuilder = new ChatContextBuilder(workspaceService);
        actionDispatcher = new ChatActionDispatcher(workspaceService, this::sendAutomated);
        initProvider();
    }

    private void createToolbar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayout(new GridLayout(8, false));
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

        stopButton = new Button(bar, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addListener(SWT.Selection, e -> cancelStreaming());

        copyCodeButton = new Button(bar, SWT.PUSH);
        copyCodeButton.setText("Copy code");
        copyCodeButton.addListener(SWT.Selection, e -> copyLastCodeBlock());

        applyCodeButton = new Button(bar, SWT.PUSH);
        applyCodeButton.setText("Apply code");
        applyCodeButton.addListener(SWT.Selection, e -> applyLastCodeBlock());

    }

    private void createChatArea(Composite parent) {
        chatArea = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
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
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayout(new GridLayout(4, false));
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        statusText = new Text(bar, SWT.BORDER | SWT.READ_ONLY);
        statusText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusText.setText("[INFO] Listo");

        progressText = new Text(bar, SWT.BORDER | SWT.READ_ONLY);
        GridData gd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        gd.widthHint = 120;
        progressText.setLayoutData(gd);
        progressText.setText("0/0");

        spinnerLabel = new Button(bar, SWT.PUSH);
        spinnerLabel.setText("⟳");
        spinnerLabel.setEnabled(false);
        spinnerLabel.setToolTipText("Estado de streaming");

        errorsButton = new Button(bar, SWT.PUSH);
        errorsButton.setText("Errores 0");
        errorsButton.setToolTipText("Ver registro de errores recientes");
        errorsButton.addListener(SWT.Selection, e -> showErrorLog());
    }

    private void loadHistory() {
        int limit = resolveHistoryLimit();
        List<ChatMessage> saved = historyStore.load(resolveHistoryProject(), limit);
        if (saved.isEmpty()) return;

        for (ChatMessage msg : saved) {
            if (msg == null) continue;
            chatHistory.add(msg);
            String role = msg.getRole() == null ? "" : msg.getRole().toLowerCase();
            String content = msg.getContent() == null ? "" : msg.getContent();
            switch (role) {
                case "user" -> appendUser(content);
                case "assistant" -> {
                    appendAIHeader();
                    appendFormatted(content);
                }
                default -> appendSystem(content);
            }
        }
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
        sendInternal(null, true);
    }

    private void sendAutomated(String payload) {
        sendInternal(payload, false);
    }

    private void sendInternal(String overrideMessage, boolean echoInChat) {
        String rawMessage = overrideMessage != null ? overrideMessage : input.getText();
        if (rawMessage == null) return;
        String message = rawMessage.trim();
        if (message.isEmpty()) return;

        if (echoInChat && handleShorthand(message)) {
            return;
        }

        clearCancelHandle();
        startSpinner();

        String providerMessage = requiresAsciiOnly()
                ? sanitizeForOllama(message).trim()
                : message;

        if (providerMessage.isEmpty()) {
            statusError("El mensaje contiene caracteres no soportados para este proveedor");
            return;
        }

        if (overrideMessage == null) {
            input.setText("");
        }

        if (echoInChat) {
            appendUser(message);
        }

        chatHistory.add(new ChatMessage("user", message));
        trimHistory();
        persistHistory();

        appendAIHeader();
        statusInfo("Armando contexto...");

        String context = contextBuilder.buildContext(chatHistory);
        if (requiresAsciiOnly()) {
            context = sanitizeForOllama(context);
        }
        context = limitContext(context);
        int estimatedTokens = (context.length() + providerMessage.length()) / 4;
        StringBuilder buffer = new StringBuilder();
        Display display = Display.getDefault();
        resetProgress();

        statusInfo("Enviando mensaje (~" + estimatedTokens + " tokens)...");
        stopButton.setEnabled(true);
        progressInfo(0, estimatedTokens);
        currentCancel = aiService.sendMessageStreaming(
            providerMessage,
            context,
            chunk -> display.asyncExec(() -> {
                buffer.append(chunk);
                statusInfo("Recibiendo...");
                progressInfo(buffer.length() / 4, estimatedTokens);
            }),
            err -> display.asyncExec(() -> {
                appendSystem("ERROR: " + err.getMessage());
                statusError(err.getMessage());
                clearCancelHandle();
                stopSpinner();
                resetProgress();
            }),
            () -> display.asyncExec(() -> {
                String text = buffer.toString();
                actionDispatcher.handle(text);
                appendFormatted(text);
                chatHistory.add(new ChatMessage("assistant", text));
                trimHistory();
                persistHistory();
                statusInfo("Respuesta completada");
                clearCancelHandle();
                stopSpinner();
                progressInfo(buffer.length() / 4, estimatedTokens);
            })
        );
    }

    // ===============================
    // FORMATO TEXTO / CÓDIGO
    // ===============================
    private void appendFormatted(String text) {
        if (text == null || text.isEmpty()) return;

        int start = aiMessageStart >= 0 ? aiMessageStart : chatArea.getCharCount();
        renderContent(text);
        chatArea.append("\n\n");
        scrollToEnd();

        int end = chatArea.getCharCount();
        applyBubble(start, end - start, aiBubble, aiTextColor);
        aiMessageStart = -1;
    }

    private void appendPlain(String txt) {
        if (!txt.isEmpty()) {
            markdownRenderer.append(chatArea, txt);
        }
    }

    private void renderContent(String text) {
        String normalized = CodeFenceHelper.normalizeFenceSyntax(text);
        Matcher m = CodeFenceHelper.codeBlockMatcher(normalized);

        int last = 0;
        while (m.find()) {
            appendPlain(normalized.substring(last, m.start()));
            appendCode(m.group(2));
            last = m.end();
        }
        appendPlain(normalized.substring(last));
    }

    private void appendCode(String code) {
        int start = chatArea.getCharCount();
        String block = ensureTrailingNewline(code);
        chatArea.append(block);

        StyleRange s = new StyleRange();
        s.start = start;
        s.length = block.length();
        s.font = monoFont;
        s.background = codeBackground;
        s.borderStyle = SWT.BORDER;
        s.borderColor = codeBorder;
        chatArea.setStyleRange(s);
        applySyntaxHighlight(block, start);
        lastCodeBlock = code;
    }

    private String ensureTrailingNewline(String code) {
        if (code == null || code.isEmpty()) {
            return "\n";
        }
        return code.endsWith("\n") ? code : code + "\n";
    }

    private void applySyntaxHighlight(String block, int baseOffset) {
        applyPattern(block, COMMENT_PATTERN, commentColor, SWT.NORMAL, baseOffset);
        applyPattern(block, STRING_PATTERN, stringColor, SWT.NORMAL, baseOffset);
        applyPattern(block, KEYWORD_PATTERN, keywordColor, SWT.BOLD, baseOffset);
    }

    private void applyPattern(String block, Pattern pattern, Color color, int fontStyle, int baseOffset) {
        if (color == null) return;
        Matcher matcher = pattern.matcher(block);
        while (matcher.find()) {
            StyleRange range = new StyleRange();
            range.start = baseOffset + matcher.start();
            range.length = matcher.end() - matcher.start();
            range.foreground = color;
            range.fontStyle = fontStyle;
            chatArea.setStyleRange(range);
        }
    }

    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b"
    );

    private static final Pattern STRING_PATTERN = Pattern.compile(
        "\"(?:\\\\.|[^\\\\\"])*\""
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile(
        "//.*?$|/\\*.*?\\*/",
        Pattern.DOTALL | Pattern.MULTILINE
    );

    private void trimHistory() {
        int limit = resolveHistoryLimit();
        while (chatHistory.size() > limit) {
            chatHistory.remove(0);
        }
    }

    private void persistHistory() {
        historyStore.save(resolveHistoryProject(), chatHistory, resolveHistoryLimit());
    }

    private String resolveHistoryProject() {
        String project = workspaceService.getActiveProjectName();
        return project == null ? "" : project;
    }

    private int resolveHistoryLimit() {
        int value = CredentialsService.preferenceStore()
            .getInt(PreferenceConstants.CHAT_MAX_HISTORY);
        if (value <= 0) {
            return 50;
        }
        return Math.min(value, 500);
    }

    // ===============================
    // HELPERS
    // ===============================
    private void appendUser(String msg) {
        int start = chatArea.getCharCount();
        chatArea.append("You:\n");

        StyleRange s = new StyleRange();
        s.start = start;
        s.length = 4;
        s.fontStyle = SWT.BOLD;
        chatArea.setStyleRange(s);

        renderContent(msg);
        chatArea.append("\n\n");

        applyBubble(start, chatArea.getCharCount() - start, userBubble, userTextColor);
        scrollToEnd();
    }

    private void appendAIHeader() {
        aiMessageStart = chatArea.getCharCount();
        chatArea.append("AI:\n");
        scrollToEnd();
    }

    private void appendSystem(String msg) {
        int start = chatArea.getCharCount();
        chatArea.append("[SYSTEM] " + msg + "\n\n");
        applyBubble(start, chatArea.getCharCount() - start, systemBubble, systemTextColor);
        scrollToEnd();
    }

    private void statusInfo(String msg) {
        updateStatus("INFO", msg);
    }

    private void statusError(String msg) {
        updateStatus("ERROR", msg);
        logError(msg);
    }

    private void updateStatus(String level, String msg) {
        Display.getDefault().asyncExec(() -> {
            if (!statusText.isDisposed()) {
                statusText.setText("[" + level + "] " + msg);
            }
        });
    }

    private void progressInfo(int currentTokens, int totalTokens) {
        Display.getDefault().asyncExec(() -> {
            if (progressText != null && !progressText.isDisposed()) {
                progressText.setText(currentTokens + "/" + totalTokens);
            }
        });
    }

    private void resetProgress() {
        progressInfo(0, 0);
    }

    private void applyBubble(int start, int length, Color background) {
        applyBubble(start, length, background, null);
    }

    private void applyBubble(int start, int length, Color background, Color foreground) {
        if (length <= 0 || background == null) return;
        StyleRange range = new StyleRange();
        range.start = start;
        range.length = length;
        range.background = background;
        if (foreground != null) {
            range.foreground = foreground;
        }
        chatArea.setStyleRange(range);
    }

    private void logError(String msg) {
        if (msg == null || msg.isBlank()) return;
        errorLog.add(msg);
        if (errorLog.size() > 50) {
            errorLog.remove(0);
        }
        updateErrorBadge();
    }

    private void updateErrorBadge() {
        if (errorsButton == null || errorsButton.isDisposed()) return;
        errorsButton.setText("Errores " + errorLog.size());
    }

    private void showErrorLog() {
        String title = "Registro de errores";
        if (errorLog.isEmpty()) {
            MessageDialog.openInformation(getSite().getShell(), title, "Sin errores recientes");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, errorLog.size() - 20);
        for (int i = start; i < errorLog.size(); i++) {
            sb.append(i + 1).append(") ").append(errorLog.get(i)).append("\n");
        }
        MessageDialog.openInformation(getSite().getShell(), title, sb.toString());
    }

    private void startSpinner() {
        Display.getDefault().asyncExec(() -> {
            if (spinnerLabel != null && !spinnerLabel.isDisposed()) {
                spinnerLabel.setText("⏳");
            }
        });
    }

    private void stopSpinner() {
        Display.getDefault().asyncExec(() -> {
            if (spinnerLabel != null && !spinnerLabel.isDisposed()) {
                spinnerLabel.setText("⟳");
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

    private void scrollToEnd() {
        if (chatArea == null || chatArea.isDisposed()) return;
        int lastLine = Math.max(chatArea.getLineCount() - 1, 0);
        chatArea.setTopIndex(lastLine);
        chatArea.setSelection(chatArea.getCharCount());
    }

    private boolean handleShorthand(String message) {
        if (message == null || !message.startsWith("/")) {
            return false;
        }

        String[] parts = message.split("\\s+");
        String cmd = parts[0].toLowerCase();
        String project = workspaceService.getActiveProjectName();

        chatHistory.add(new ChatMessage("user", message));
        trimHistory();

        switch (cmd) {
            case "/read" -> {
                if (parts.length < 2) {
                    appendSystem("Uso: /read <ruta-relativa>");
                    return true;
                }
                if (project.isEmpty()) {
                    appendSystem("No hay proyecto activo en el editor");
                    return true;
                }
                String path = message.substring(message.indexOf(' ')).trim();
                if (path.isEmpty()) {
                    appendSystem("Debes indicar la ruta");
                    return true;
                }
                appendSystem("Leyendo " + path);
                String content = workspaceService.readFile(project, path);
                if (content == null || content.isBlank()) {
                    appendSystem("No se pudo leer el archivo");
                } else {
                    appendFormatted(content);
                    chatHistory.add(new ChatMessage("assistant", content));
                }
                trimHistory();
                persistHistory();
                return true;
            }
            case "/list" -> {
                if (project.isEmpty()) {
                    appendSystem("No hay proyecto activo en el editor");
                    return true;
                }
                int depth = clamp(parseOrDefault(parts, 1, CredentialsService.preferenceStore().getInt(PreferenceConstants.LIST_MAX_DEPTH)), 1, 10);
                int limit = clamp(parseOrDefault(parts, 2, CredentialsService.preferenceStore().getInt(PreferenceConstants.LIST_MAX_LIMIT)), 10, 1000);
                String listing = workspaceService.listProjectTree(project, depth, limit);
                appendSystem("Listado de archivos (" + project + ") depth=" + depth + " limit=" + limit);
                appendFormatted(listing);
                chatHistory.add(new ChatMessage("assistant", listing));
                trimHistory();
                persistHistory();
                return true;
            }
            case "/ctx" -> {
                appendSystem("Snapshot del workspace...");
                String snapshot = workspaceService.readWorkspaceSnapshot();
                appendFormatted(snapshot);
                chatHistory.add(new ChatMessage("assistant", snapshot));
                trimHistory();
                persistHistory();
                return true;
            }
            case "/doc" -> {
                String path = parts.length >= 2 ? parts[1] : "README.md";
                if (project.isEmpty()) {
                    appendSystem("No hay proyecto activo en el editor");
                    return true;
                }
                String content = workspaceService.readFile(project, path);
                if (content == null || content.isBlank()) {
                    appendSystem("No se pudo leer " + path);
                } else {
                    appendFormatted(content);
                    chatHistory.add(new ChatMessage("assistant", content));
                    trimHistory();
                    persistHistory();
                }
                return true;
            }
            case "/recent" -> {
                int n = parts.length >= 2 ? clamp(parseOrDefault(parts, 1, 5), 1, 20) : 5;
                appendSystem("Últimos " + n + " mensajes:");
                List<ChatMessage> tail = chatHistory.subList(Math.max(0, chatHistory.size() - n), chatHistory.size());
                StringBuilder sb = new StringBuilder();
                for (ChatMessage msg : tail) {
                    sb.append(msg.getRole()).append(": ").append(snippet(msg.getContent())).append("\n");
                }
                appendFormatted(sb.toString());
                chatHistory.add(new ChatMessage("assistant", sb.toString()));
                trimHistory();
                persistHistory();
                return true;
            }
            case "/history" -> {
                if (parts.length < 2) {
                    appendSystem("Uso: /history <termino>");
                    return true;
                }
                String term = message.substring(message.indexOf(' ')).trim().toLowerCase();
                StringBuilder hits = new StringBuilder();
                int count = 0;
                for (ChatMessage msg : chatHistory) {
                    if (msg.getContent() != null && msg.getContent().toLowerCase().contains(term)) {
                        hits.append(msg.getRole()).append(": ").append(snippet(msg.getContent())).append("\n");
                        count++;
                        if (count >= 20) break;
                    }
                }
                if (count == 0) {
                    appendSystem("Sin coincidencias para '" + term + "'");
                } else {
                    appendSystem("Coincidencias: " + count);
                    appendFormatted(hits.toString());
                    chatHistory.add(new ChatMessage("assistant", hits.toString()));
                    trimHistory();
                    persistHistory();
                }
                return true;
            }
            case "/clearhistory" -> {
                chatHistory.clear();
                persistHistory();
                historyStore.clear(project);
                chatArea.setText("");
                appendSystem("Historial borrado para proyecto " + (project.isEmpty() ? "global" : project));
                return true;
            }
            default -> {
                chatHistory.remove(chatHistory.size() - 1);
                return false;
            }
        }
    }

    private int parseOrDefault(String[] parts, int index, int defaultValue) {
        if (parts == null || index >= parts.length) return defaultValue;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private String snippet(String text) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ');
        if (clean.length() <= 140) return clean;
        return clean.substring(0, 140) + "…";
    }

    private void cancelStreaming() {
        if (currentCancel != null) {
            currentCancel.run();
            appendSystem("Respuesta cancelada");
            statusInfo("Cancelado");
            clearCancelHandle();
        }
    }

    private void clearCancelHandle() {
        currentCancel = null;
        stopButton.setEnabled(false);
    }

    private void copyLastCodeBlock() {
        if (lastCodeBlock == null || lastCodeBlock.isBlank()) {
            statusError("No hay bloque de código reciente para copiar");
            return;
        }
        Clipboard cb = new Clipboard(Display.getDefault());
        cb.setContents(new Object[] { lastCodeBlock }, new Transfer[] { TextTransfer.getInstance() });
        cb.dispose();
        statusInfo("Código copiado al portapapeles");
    }

    private void applyLastCodeBlock() {
        if (lastCodeBlock == null || lastCodeBlock.isBlank()) {
            statusError("No hay bloque de código reciente para aplicar");
            return;
        }
        String currentSelection = workspaceService.getActiveSelectionText();
        if (currentSelection == null) currentSelection = "";

        boolean replaceWholeFile = currentSelection.isEmpty();
        String original = replaceWholeFile
            ? workspaceService.getActiveEditorContent()
            : currentSelection;

        if (original == null) original = "";

        String diff = diffService.diff(original, lastCodeBlock);
        boolean ok = ApplyChangesDialog.confirm(getSite().getShell(), diff);
        if (!ok) {
            statusInfo("Aplicación cancelada");
            return;
        }

        boolean applied = replaceWholeFile
            ? workspaceService.applyFullToActiveEditor(lastCodeBlock)
            : workspaceService.applyToActiveEditor(lastCodeBlock);

        if (applied) {
            statusInfo("Código aplicado en el editor activo");
        } else {
            statusError("No se pudo aplicar el código (sin editor activo)");
        }
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
