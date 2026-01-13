package com.aihelper.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;

import com.aihelper.ai.*;
import com.aihelper.model.ChatMessage;
import com.aihelper.ui.chat.*;
import com.aihelper.workspace.DiffService;
import com.aihelper.workspace.WorkspaceService;

public class ChatView extends ViewPart {

    public static final String ID = "com.aihelper.ui.chatView";

    // ===============================
    // UI
    // ===============================
    private StyledText chatArea;
    private Text input;
    private Combo providerCombo;
    private Combo modelCombo;
    private Text statusText;
    private Text progressText;
    private Button spinnerButton;
    private Button errorsButton;
    private final WorkspaceService workspaceService = new WorkspaceService();
    private final DiffService diffService = new DiffService();
    private ChatController controller;
    private AiChatService aiService;
    private ChatContextBuilder contextBuilder;
    private ChatActionDispatcher actionDispatcher;

    // ===============================
    // State
    // ===============================
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> errorLog = new ArrayList<>();
    private Runnable currentCancel;
    private String lastCodeBlock = "";
    private int aiMessageStart = -1;
    private StringBuilder aiResponseBuffer = new StringBuilder();

    // ===============================
    // Styling
    // ===============================
    private Font monoFont;
    private Color userBubble;
    private Color aiBubble;
    private Color systemBubble;
    private Color userTextColor;
    private Color aiTextColor;
    private Color systemTextColor;

    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private Image copilotIcon;

    // ===============================
    // Lifecycle
    // ===============================
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        initColors();
        createToolbar(parent);
        createChatArea(parent);
        createInputBar(parent);
        createStatusBar(parent);

        // Set logo in the tab (opcional, no en la vista)
        try {
            Image logo = new Image(parent.getDisplay(), getClass().getResourceAsStream("/icons/aihelper-icon.png"));
            setTitleImage(logo);
        } catch (Exception e) {
            // Ignore if logo not found
        }

        contextBuilder = new ChatContextBuilder(workspaceService);
        // Instanciar el dispatcher para acciones automáticas
        actionDispatcher = new ChatActionDispatcher(workspaceService, this::appendAutomatedUser);

        loadHistory();
        initProvider();
    }

    // ===============================
    // TOOLBAR
    // ===============================
    private void createToolbar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(9, false));

        // Logo pequeño a la izquierda, imagen realmente 24x24
        Label logoLabel = new Label(bar, SWT.NONE);
        GridData logoData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        logoLabel.setLayoutData(logoData);
        try {
            ImageData imgData = new ImageData(getClass().getResourceAsStream("/icons/aihelper-icon.png"));
            Image logoImg = new Image(bar.getDisplay(), imgData.scaledTo(24, 24));
            logoLabel.setImage(logoImg);
        } catch (Exception e) {
            // Ignore if logo not found
        }

        providerCombo = new Combo(bar, SWT.READ_ONLY);
        providerCombo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        providerCombo.setItems("Ollama", "OpenAI", "Gemini", "Qwen", "DeepSeek");
        providerCombo.select(0);
        providerCombo.setToolTipText("Selecciona el proveedor de IA");
        providerCombo.addListener(SWT.Selection, e -> switchProvider());

        modelCombo = new Combo(bar, SWT.READ_ONLY);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelCombo.setToolTipText("Selecciona el modelo de IA");
        modelCombo.addListener(SWT.Selection, e -> {
            aiService.setModel(modelCombo.getText());
            statusInfo("Modelo activo: " + modelCombo.getText());
        });

        Button clear = new Button(bar, SWT.PUSH);
        clear.setText("Clear");
        clear.setToolTipText("Limpiar chat");
        clear.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        clear.addListener(SWT.Selection, e -> {
            chatArea.setText("");
            chatHistory.clear();
            statusInfo("Historial limpiado");
        });

        Button prefs = new Button(bar, SWT.PUSH);
        prefs.setText("⚙");
        prefs.setToolTipText("Configurar credenciales y endpoints");
        prefs.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        prefs.addListener(SWT.Selection, e -> openPreferences());

        Button stopButton = new Button(bar, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setToolTipText("Cancelar respuesta actual");
        stopButton.setEnabled(false);
        stopButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        stopButton.addListener(SWT.Selection, e -> cancelStreaming());

        Button copyCodeButton = new Button(bar, SWT.PUSH);
        copyCodeButton.setText("Copy code");
        copyCodeButton.setToolTipText("Copiar último bloque de código");
        copyCodeButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        copyCodeButton.addListener(SWT.Selection, e -> copyLastCodeBlock());

        Button applyCodeButton = new Button(bar, SWT.PUSH);
        applyCodeButton.setText("Apply code");
        applyCodeButton.setToolTipText("Aplicar último bloque de código al editor activo");
        applyCodeButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        applyCodeButton.addListener(SWT.Selection, e -> applyLastCodeBlock());
    }

    public void openPreferences() {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
            getSite().getShell(),
            "com.aihelper.preferences.main",
            null,
            null
        );
        if (dialog != null) {
            dialog.open();
        }
    }

    // ===============================
    // CHAT AREA
    // ===============================
    private void createChatArea(Composite parent) {
        chatArea = new StyledText(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.WRAP);
        chatArea.setEditable(false);
        chatArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        // Cargar el icono Copilot o AI
        try {
            copilotIcon = new Image(parent.getDisplay(), getClass().getResourceAsStream("/icons/aihelper-icon.png"));
        } catch (Exception e) {
            copilotIcon = null;
        }
    }

    // ===============================
    // INPUT
    // ===============================
    private void createInputBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        bar.setLayout(new GridLayout(2, false));

        input = new Text(bar, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.heightHint = input.getLineHeight() * 4;
        input.setLayoutData(gd);

        input.setToolTipText("Escribe tu mensaje aquí. Ctrl+Enter para enviar.");
        input.addListener(SWT.KeyDown, e -> {
            if ((e.stateMask & SWT.CTRL) != 0 && (e.keyCode == SWT.CR || e.keyCode == SWT.LF)) {
                send();
            }
        });

        Button sendButton = new Button(bar, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.setToolTipText("Enviar mensaje");
        sendButton.addListener(SWT.Selection, e -> send());
    }

    // ===============================
    // STATUS BAR
    // ===============================
    private void createStatusBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        bar.setLayout(new GridLayout(4, false));

        statusText = new Text(bar, SWT.READ_ONLY | SWT.BORDER);
        statusText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusText.setText("[INFO] Ready");

        progressText = new Text(bar, SWT.READ_ONLY | SWT.BORDER);
        progressText.setLayoutData(new GridData(120, SWT.DEFAULT));
        progressText.setText("Tokens: 0");

        spinnerButton = new Button(bar, SWT.PUSH);
        spinnerButton.setText("⟳");
        spinnerButton.setEnabled(false);
        spinnerButton.setToolTipText("Estado de streaming");

        errorsButton = new Button(bar, SWT.PUSH);
        errorsButton.setText("Errors 0");
        errorsButton.setToolTipText("Ver registro de errores recientes");
        errorsButton.addListener(SWT.Selection, e -> showErrorLog());
    }

    // ===============================
    // PROVIDER
    // ===============================
    private void initProvider() {
        switchProvider();
    }

    private void switchProvider() {
        aiService = switch (providerCombo.getText()) {
            case "OpenAI" -> new OpenAiChatService();
            case "Gemini" -> new GeminiChatService();
            case "Qwen" -> new QwenChatService();
            case "DeepSeek" -> new DeepSeekChatService();
            default -> new OllamaChatService();
        };
        loadModels();
    }

    private void loadModels() {
        CompletableFuture
            .supplyAsync(aiService::listModels)
            .thenAccept(models -> Display.getDefault().asyncExec(() -> {
                modelCombo.setItems(models.toArray(String[]::new));
                modelCombo.select(0);
                aiService.setModel(models.get(0));
            }));
    }

    // ===============================
    // CHAT FLOW
    // ===============================
    private void send() {
        sendInternal(null);
    }

    private void sendInternal(String override) {
        String msg = override != null ? override : input.getText().trim();
        if (msg.isEmpty()) return;

        input.setText("");
        appendUser(msg);
        chatHistory.add(new ChatMessage("user", msg));

        appendAIHeader();
        setStreamingState(true);
        aiResponseBuffer.setLength(0); // Limpiar buffer de respuesta IA
        aiService.sendMessageStreaming(
            msg,
            contextBuilder.buildContext(chatHistory),
            chunk -> Display.getDefault().asyncExec(() -> {
                aiResponseBuffer.append(chunk);
                updateProgressTokens(chunk != null ? chunk.length() : 0);
            }),
            err -> Display.getDefault().asyncExec(() -> {
                appendSystem(err.getMessage());
                errorLog.add(err.getMessage());
                updateErrorCount();
                setStreamingState(false);
            }),
            () -> Display.getDefault().asyncExec(() -> {
                setStreamingState(false);
                // Al finalizar, mostrar la respuesta completa de la IA en su globo
                String aiResponse = aiResponseBuffer.toString();
                if (!aiResponse.isEmpty()) {
                    // Si es solo un action, no mostrar en chatArea pero sí agregar al histórico
                    if (isOnlyAction(aiResponse)) {
                        chatHistory.add(new ChatMessage("assistant", aiResponse));
                        actionDispatcher.handle(aiResponse);
                    } else {
                        appendAIResponse(aiResponse);
                        chatHistory.add(new ChatMessage("assistant", aiResponse));
                        actionDispatcher.handle(aiResponse);
                    }
                }
            })
        );
    }

    // ===============================
    // RENDER
    // ===============================
    private void appendUser(String msg) {
        // Línea en blanco antes del globo (solo uno para menor separación)
        chatArea.append("\n");
        int start = chatArea.getCharCount();
        String userLabel = "You:";
        chatArea.append(userLabel + "\n");
        StyleRange s = new StyleRange();
        s.start = start;
        s.length = userLabel.length();
        s.fontStyle = SWT.BOLD;
        chatArea.setStyleRange(s);
        int msgStart = chatArea.getCharCount();
        renderMarkdownContent(msg);
        // Solo un salto de línea después del globo
        chatArea.append("\n");
        applyBubble(start, chatArea.getCharCount() - start, userBubble, userTextColor);
        scrollToEnd();
    }

    private void appendAIHeader() {
        // Línea en blanco antes del globo
        chatArea.append("\n");
        aiMessageStart = chatArea.getCharCount();
        // Insertar espacio para el icono
        chatArea.append(" ");
        if (copilotIcon != null) {
            StyleRange iconRange = new StyleRange();
            iconRange.start = aiMessageStart;
            iconRange.length = 1;
            iconRange.data = copilotIcon;
            chatArea.setStyleRange(iconRange);
        }
        chatArea.append("\n");
        scrollToEnd();
    }

    private void appendAIResponse(String aiResponse) {
        // Filtrar action e instrucciones internas para el modelo
        String userVisible = extractUserVisible(aiResponse);
        if (userVisible.isBlank()) {
            // No mostrar nada si solo hay action/instrucción
            return;
        }
        // Línea en blanco antes del globo (solo uno)
        chatArea.append("\n");
        int start = chatArea.getCharCount();
        String aiLabel = "AI:";
        // Insertar icono Copilot/AI antes del label
        if (copilotIcon != null) {
            chatArea.append(" ");
            StyleRange iconRange = new StyleRange();
            iconRange.start = chatArea.getCharCount() - 1;
            iconRange.length = 1;
            iconRange.data = copilotIcon;
            chatArea.setStyleRange(iconRange);
        }
        chatArea.append(aiLabel + "\n");
        StyleRange s = new StyleRange();
        s.start = start;
        s.length = aiLabel.length();
        s.fontStyle = SWT.BOLD;
        chatArea.setStyleRange(s);
        int msgStart = chatArea.getCharCount();
        renderMarkdownContent(userVisible);
        // Solo un salto de línea después del globo
        chatArea.append("\n");
        applyBubble(start, chatArea.getCharCount() - start, aiBubble, aiTextColor);
        scrollToEnd();
    }

    private void renderMarkdownContent(String text) {
        if (text == null || text.isEmpty()) return;
        markdownRenderer.append(chatArea, text);
    }

    private boolean isOnlyAction(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        // Detecta si es solo un action (línea única con [ACTION:...])
        boolean onlyAction = trimmed.matches("^\\[ACTION:[^\\]]+].*$") ||
               trimmed.matches("^\\[ACTION:[^\\]]+].*\\n*$");
        // Detecta si es solo instrucciones internas
        boolean onlyInstruction = trimmed.matches("(?s)^Puedes decidir si necesitas ejecutar más acciones.*usuario\\.?$");
        // Detecta si es action + instrucción (en cualquier orden, con o sin saltos de línea)
        boolean actionAndInstruction =
            (onlyAction && trimmed.contains("Puedes decidir si necesitas ejecutar más acciones")) ||
            (onlyInstruction && trimmed.contains("[ACTION:"));
        return onlyAction || onlyInstruction || actionAndInstruction;
    }

    private String extractUserVisible(String text) {
        if (text == null) return "";
        String result = text;
        // Elimina líneas que sean solo [ACTION:...]
        result = result.replaceAll("(?m)^\\[ACTION:[^\\]]+].*$", "");
        // Elimina instrucciones internas para el modelo
        result = result.replaceAll("(?s)Puedes decidir si necesitas ejecutar más acciones.*usuario\\.?$", "");
        // Elimina espacios y saltos de línea sobrantes
        result = result.replaceAll("^[ \t\r\n]+|[ \t\r\n]+$", "");
        return result;
    }

    private Color getCodeBlockColor(String language) {
        if (language == null) return userBubble;
        switch (language.toLowerCase()) {
            case "java": return Display.getDefault().getSystemColor(SWT.COLOR_YELLOW);
            case "python": return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            case "xml": return Display.getDefault().getSystemColor(SWT.COLOR_CYAN);
            case "json": return Display.getDefault().getSystemColor(SWT.COLOR_MAGENTA);
            case "bash":
            case "sh": return Display.getDefault().getSystemColor(SWT.COLOR_GRAY);
            case "c": return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            case "cpp":
            case "c++": return Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED);
            case "js":
            case "javascript": return Display.getDefault().getSystemColor(SWT.COLOR_DARK_YELLOW);
            case "ts":
            case "typescript": return Display.getDefault().getSystemColor(SWT.COLOR_DARK_CYAN);
            case "html": return Display.getDefault().getSystemColor(SWT.COLOR_DARK_MAGENTA);
            case "css": return Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
            case "sql": return Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN);
            case "inline": return Display.getDefault().getSystemColor(SWT.COLOR_WHITE);
            default: return userBubble;
        }
    }

    private void renderContent(String text) {
        if (text == null || text.isEmpty()) return;
        String normalized = CodeFenceHelper.normalizeFenceSyntax(text);
        List<CodeFenceHelper.CodeBlock> blocks = CodeFenceHelper.extractCodeBlocks(normalized);
        int last = 0;
        Matcher matcher = CodeFenceHelper.codeBlockMatcher(normalized);
        for (CodeFenceHelper.CodeBlock block : blocks) {
            matcher.find();
            int start = matcher.start();
            int end = matcher.end();
            // Separación visual extra antes del bloque
            chatArea.append("\n");
            // Renderiza el texto antes del bloque
            if (start > last) {
                String before = normalized.substring(last, start);
                renderInlineCode(before);
            }
            // Mostrar el lenguaje encima del bloque
            if (!block.language.isEmpty()) {
                int langStart = chatArea.getCharCount();
                chatArea.append(block.language + "\n");
                StyleRange langStyle = new StyleRange();
                langStyle.start = langStart;
                langStyle.length = block.language.length();
                langStyle.fontStyle = SWT.BOLD;
                langStyle.foreground = Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN);
                chatArea.setStyleRange(langStyle);
            }
            // Renderiza el bloque de código con color diferenciado y tooltip
            appendCodeStyledWithTooltip(block.content, block.language);
            // Separación visual extra después del bloque
            chatArea.append("\n");
            last = end;
        }
        // Renderiza el texto restante después del último bloque
        if (last < normalized.length()) {
            renderInlineCode(normalized.substring(last));
        }
    }

    private void renderInlineCode(String text) {
        if (text == null || text.isEmpty()) return;
        int idx = 0;
        Matcher m = Pattern.compile("`([^`]+)`").matcher(text);
        while (m.find()) {
            // Texto antes del bloque inline
            if (m.start() > idx) {
                markdownRenderer.append(chatArea, text.substring(idx, m.start()));
            }
            // Bloque inline
            int codeStart = chatArea.getCharCount();
            chatArea.append(m.group(1));
            StyleRange s = new StyleRange();
            s.start = codeStart;
            s.length = m.group(1).length();
            s.font = monoFont;
            s.background = getCodeBlockColor("inline");
            chatArea.setStyleRange(s);
            idx = m.end();
        }
        // Texto restante
        if (idx < text.length()) {
            markdownRenderer.append(chatArea, text.substring(idx));
        }
    }

    private void appendCodeStyledWithTooltip(String code, String language) {
        int start = chatArea.getCharCount();
        String block = ensureTrailingNewline(code);
        chatArea.append(block);
        StyleRange s = new StyleRange();
        s.start = start;
        s.length = block.length();
        s.font = monoFont;
        s.background = getCodeBlockColor(language);
        // Mejor contraste para accesibilidad
        s.foreground = Display.getDefault().getSystemColor(SWT.COLOR_BLACK);
        // Tooltip con información del bloque
        s.data = "Lenguaje: " + (language.isEmpty() ? "(desconocido)" : language) + ", líneas: " + block.split("\n").length;
        chatArea.setStyleRange(s);
        lastCodeBlock = code;
        // Permitir copiar el bloque con doble clic
        chatArea.addListener(SWT.MouseDoubleClick, e -> {
            int offset = chatArea.getOffsetAtLocation(e.display.map(chatArea, null, e.x, e.y));
            if (offset >= start && offset < start + block.length()) {
                Clipboard cb = new Clipboard(Display.getDefault());
                cb.setContents(new Object[]{code}, new Transfer[]{TextTransfer.getInstance()});
                cb.dispose();
                statusInfo("Bloque de código copiado al portapapeles");
            }
        });
        // No aplicar applyBubble aquí: los bloques de código no deben tener burbuja/borde
    }

    // Helper para restaurar mensajes del historial
    private void appendMessage(String role, String content) {
        if (role == null) role = "";
        if (content == null) content = "";
        switch (role.toLowerCase()) {
            case "user" -> appendUser(content);
            case "assistant" -> {
                appendAIHeader();
                int msgStart = chatArea.getCharCount();
                chatArea.append(content + "\n");
                // Fondo IA, alineación izquierda
                applyBubble(msgStart, chatArea.getCharCount() - msgStart, aiBubble, aiTextColor);
                // Línea en blanco después del globo
                chatArea.append("\n");
                aiMessageStart = -1;
            }
            default -> appendSystem(content);
        }
    }

    // ===============================
    // HELPERS
    // ===============================
    private void copyLastCodeBlock() {
        Clipboard cb = new Clipboard(Display.getDefault());
        cb.setContents(new Object[]{lastCodeBlock}, new Transfer[]{TextTransfer.getInstance()});
        cb.dispose();
        statusInfo("Código copiado al portapapeles");
    }

    private void applyLastCodeBlock() {
        String diff = diffService.diff(
            workspaceService.getActiveEditorContent(),
            lastCodeBlock
        );
        if (ApplyChangesDialog.confirm(getSite().getShell(), diff)) {
            workspaceService.applyFullToActiveEditor(lastCodeBlock);
            statusInfo("Código aplicado en el editor activo");
        }
    }

    private void loadHistory() {
        String project = workspaceService.getActiveProjectName();
        if (controller != null) {
            for (ChatMessage msg : controller.loadHistory(project)) {
                appendMessage(msg.getRole(), msg.getContent());
            }
        }
    }
    
    private void showErrorLog() {
        MessageDialog.openInformation(getSite().getShell(), "Errors", String.join("\n", errorLog));
    }

    private void cancelStreaming() {
        if (currentCancel != null) currentCancel.run();
        statusInfo("Respuesta cancelada");
    }

    private void initColors() {
        Display d = Display.getDefault();
        monoFont = new Font(d, "Consolas", 10, SWT.NORMAL);
        userBubble = d.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        aiBubble = d.getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT);
        systemBubble = d.getSystemColor(SWT.COLOR_GRAY);
        userTextColor = d.getSystemColor(SWT.COLOR_BLACK);
        aiTextColor = d.getSystemColor(SWT.COLOR_DARK_BLUE);
        systemTextColor = d.getSystemColor(SWT.COLOR_WHITE);
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

    private void statusInfo(String msg) {
        updateStatus("INFO", msg);
    }

    private void updateStatus(String level, String msg) {
        Display.getDefault().asyncExec(() -> {
            if (!statusText.isDisposed()) {
                statusText.setText("[" + level + "] " + msg);
            }
        });
    }

    private void updateProgressTokens(int tokens) {
        if (progressText != null && !progressText.isDisposed()) {
            progressText.setText("Tokens: " + tokens);
        }
    }

    private void setStreamingState(boolean streaming) {
        if (spinnerButton != null && !spinnerButton.isDisposed()) {
            spinnerButton.setEnabled(streaming);
            spinnerButton.setText(streaming ? "⟳ (ON)" : "⟳");
        }
    }

    private void updateErrorCount() {
        if (errorsButton != null && !errorsButton.isDisposed()) {
            errorsButton.setText("Errors " + errorLog.size());
        }
    }

    private void scrollToEnd() {
        if (chatArea == null || chatArea.isDisposed()) return;
        int lastLine = Math.max(chatArea.getLineCount() - 1, 0);
        chatArea.setTopIndex(lastLine);
        chatArea.setSelection(chatArea.getCharCount());
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

    private void appendAutomatedUser(String msg) {
        // Mensaje automático generado por acción, se agrega como mensaje de usuario
        appendUser(msg);
    }

    private String ensureTrailingNewline(String code) {
        if (code == null || code.isEmpty()) {
            return "\n";
        }
        return code.endsWith("\n") ? code : code + "\n";
    }

    private void appendSystem(String msg) {
        int start = chatArea.getCharCount();
        chatArea.append("[SYSTEM] " + msg + "\n\n");
        applyBubble(start, chatArea.getCharCount() - start, systemBubble, systemTextColor);
        scrollToEnd();
    }
}
