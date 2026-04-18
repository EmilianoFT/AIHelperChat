package com.aihelper.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String ACTION_RESULT_PREFIX = "[ACTION_RESULT:";
    private static final String ACTION_FOLLOW_UP_PROMPT =
            "Use the latest ACTION_RESULT from the conversation history. If more data is needed, output exactly one action line. Otherwise answer the user directly.";

    // ===============================
    // UI
    // ===============================
    private StyledText chatArea;
    private Text input;
    private Combo providerCombo;
    private Combo modelCombo;
    private Combo viewCombo;
    private Text statusText;
    private Text progressText;
    private Button spinnerButton;
    private Button stopButton;
    private Button teamModeButton;
    private Button errorsButton;
    private final WorkspaceService workspaceService = new WorkspaceService();
    private final DiffService diffService = new DiffService();
    private final LocalWorkspaceRouter localWorkspaceRouter = new LocalWorkspaceRouter(workspaceService);
    private AiChatService aiService;
    private ChatContextBuilder contextBuilder;
    private ChatActionDispatcher actionDispatcher;
    private final ProfileConfigService profileConfigService = new ProfileConfigService();
    private final Map<ChatProfile, ChatSession> sessions = new EnumMap<>(ChatProfile.class);
    private final Map<ChatProfile, List<ChatMessage>> viewMessages = new EnumMap<>(ChatProfile.class);
    private final Map<ChatProfile, Integer> consumedTokens = new EnumMap<>(ChatProfile.class);
    private final List<ChatMessage> viewAllMessages = new ArrayList<>();

    // ===============================
    // State
    // ===============================
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> errorLog = new ArrayList<>();
    private Runnable currentCancel;
    private String lastCodeBlock = "";
    private int aiMessageStart = -1;

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

        for (ChatProfile profile : ChatProfile.values()) {
            viewMessages.put(profile, new ArrayList<>());
        }

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
        actionDispatcher = new ChatActionDispatcher(workspaceService, this::continueAutomatedConversation);

        loadHistory();
        initProvider();
    }

    // ===============================
    // TOOLBAR
    // ===============================
    private void createToolbar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(10, false));

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

        teamModeButton = new Button(bar, SWT.CHECK);
        teamModeButton.setText("Team mode");
        teamModeButton.setToolTipText("Activa 4 chats paralelos (Dev, Arquitecto, Auditor, Team Leader)");
        teamModeButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        teamModeButton.addListener(SWT.Selection, e -> refreshTotalTokenCount());

        modelCombo = new Combo(bar, SWT.READ_ONLY);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelCombo.setToolTipText("Selecciona el modelo de IA");
        modelCombo.addListener(SWT.Selection, e -> {
            aiService.setModel(modelCombo.getText());
            statusInfo("Modelo activo: " + modelCombo.getText());
        });

        viewCombo = new Combo(bar, SWT.READ_ONLY);
        viewCombo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        viewCombo.setToolTipText("Selecciona el chat a visualizar");
        viewCombo.setItems("Todos", "Team Leader", "Dev Senior", "Arquitecto Senior", "Auditor Senior");
        viewCombo.select(0);
        viewCombo.addListener(SWT.Selection, e -> renderView());

        Button clear = new Button(bar, SWT.PUSH);
        clear.setText("Clear");
        clear.setToolTipText("Limpiar chat");
        clear.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        clear.addListener(SWT.Selection, e -> {
            chatArea.setText("");
            chatHistory.clear();
            clearHistories();
            statusInfo("Historial limpiado");
        });

        Button prefs = new Button(bar, SWT.PUSH);
        prefs.setText("⚙");
        prefs.setToolTipText("Configurar credenciales y endpoints");
        prefs.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        prefs.addListener(SWT.Selection, e -> openPreferences());

        stopButton = new Button(bar, SWT.PUSH);
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
            "com.aihelper.preferences",
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
        progressText.setLayoutData(new GridData(180, SWT.DEFAULT));
        progressText.setText("Tokens total: 0");
        progressText.setToolTipText("Estimación acumulada de tokens consumidos por el chat actual.");

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
                if (models == null || models.isEmpty()) {
                    modelCombo.setItems(new String[0]);
                    modelCombo.deselectAll();
                    statusInfo("No se encontraron modelos para el proveedor seleccionado");
                    return;
                }
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
        final String projectName = workspaceService.getActiveProjectName();

        input.setText("");
        appendUser(msg);
        chatHistory.add(new ChatMessage("user", msg));
        recordUserMessageForViews(msg);

        if (tryHandleLocalWorkspaceRequest(msg, projectName)) {
            return;
        }

        if (isTeamMode()) {
            sendTeamMessage(msg, projectName);
        } else {
            sendSingleMessage(msg, projectName);
        }
    }

    private boolean tryHandleLocalWorkspaceRequest(String msg, String projectName) {
        String localResponse = localWorkspaceRouter.tryHandle(msg, projectName);
        if (localResponse == null || localResponse.isBlank()) {
            return false;
        }

        appendLocalExchangeToSessions(msg, localResponse, projectName);
        recordLocalAssistantMessageForViews(localResponse, isTeamMode());
        appendAIResponse(localResponse, "Local");
        chatHistory.add(new ChatMessage("assistant", localResponse));
        statusInfo("Respuesta local del workspace");
        currentCancel = null;
        return true;
    }

    private void appendLocalExchangeToSessions(String userMessage, String assistantMessage, String projectName) {
        if (isTeamMode()) {
            for (ChatProfile profile : ChatProfile.values()) {
                ChatSession session = resolveSession(profile, providerCombo.getText(), modelCombo.getText(), projectName);
                session.appendMessage("user", userMessage);
                session.appendMessage("assistant", assistantMessage);
            }
            return;
        }

        ChatSession leader = resolveSession(ChatProfile.TEAM_LEADER, providerCombo.getText(), modelCombo.getText(), projectName);
        leader.appendMessage("user", userMessage);
        leader.appendMessage("assistant", assistantMessage);
    }

    private void sendSingleMessage(String msg, String projectName) {
        sendSingleMessage(msg, projectName, true);
    }

    private void sendSingleMessage(String msg, String projectName, boolean appendHeader) {
        if (appendHeader) {
            appendAIHeader();
        }
        setStreamingState(true);

        ChatSession session = resolveSession(
                ChatProfile.TEAM_LEADER,
                providerCombo.getText(),
                modelCombo.getText(),
                projectName
        );
        int requestTokens = estimateRequestTokens(session, msg);

        currentCancel = session.sendMessageStreaming(
            msg,
            chunk -> Display.getDefault().asyncExec(() -> addConsumedTokens(ChatProfile.TEAM_LEADER, estimateTokens(chunk))),
            err -> Display.getDefault().asyncExec(() -> {
                appendSystem(err.getMessage());
                errorLog.add(err.getMessage());
                updateErrorCount();
                setStreamingState(false);
                currentCancel = null;
            }),
            aiResponse -> Display.getDefault().asyncExec(() -> {
                setStreamingState(false);
                currentCancel = null;
                if (aiResponse != null && !aiResponse.isEmpty()) {
                    if (isOnlyAction(aiResponse)) {
                        chatHistory.add(new ChatMessage("assistant", aiResponse));
                        actionDispatcher.handle(aiResponse);
                    } else {
                        recordAssistantMessageForViews(ChatProfile.TEAM_LEADER, "AI", aiResponse);
                        if (shouldRenderForProfile(ChatProfile.TEAM_LEADER)) {
                            appendAIResponse(aiResponse);
                        } else {
                            renderView();
                        }
                        chatHistory.add(new ChatMessage("assistant", aiResponse));
                        actionDispatcher.handle(aiResponse);
                    }
                }
            })
        );
        addConsumedTokens(ChatProfile.TEAM_LEADER, requestTokens);
    }

    private void sendTeamMessage(String msg, String projectName) {
        setStreamingState(true);
        List<ChatProfile> profiles = List.of(
                ChatProfile.DEV_SENIOR,
                ChatProfile.ARQ_SENIOR,
                ChatProfile.AUDITOR_SENIOR,
                ChatProfile.TEAM_LEADER
        );

        AtomicInteger remaining = new AtomicInteger(profiles.size());

        currentCancel = () -> {
            for (ChatProfile profile : profiles) {
                ChatSession session = sessions.get(profile);
                if (session != null) {
                    session.cancel();
                }
            }
        };

        for (ChatProfile profile : profiles) {
            String fallbackProvider = providerCombo.getText();
            String fallbackModel = modelCombo.getText();

            ChatSession session = resolveSession(profile, fallbackProvider, fallbackModel, projectName);
            int requestTokens = estimateRequestTokens(session, msg);

            session.sendMessageStreaming(
                msg,
                chunk -> Display.getDefault().asyncExec(() -> addConsumedTokens(profile, estimateTokens(chunk))),
                err -> Display.getDefault().asyncExec(() -> {
                    appendSystem("[" + profile.getDisplayName() + "] " + err.getMessage());
                    errorLog.add(err.getMessage());
                    updateErrorCount();
                    if (remaining.decrementAndGet() == 0) {
                        setStreamingState(false);
                        currentCancel = null;
                    }
                }),
                aiResponse -> Display.getDefault().asyncExec(() -> {
                    if (aiResponse != null && !aiResponse.isEmpty()) {
                        if (profile == ChatProfile.TEAM_LEADER && isOnlyAction(aiResponse)) {
                            actionDispatcher.handle(aiResponse);
                        } else {
                            recordAssistantMessageForViews(profile, profile.getDisplayName(), aiResponse);
                            if (shouldRenderForProfile(profile)) {
                                appendAIResponse(aiResponse, profile.getDisplayName());
                            } else {
                                renderView();
                            }
                            if (profile == ChatProfile.TEAM_LEADER) {
                                actionDispatcher.handle(aiResponse);
                            }
                        }
                    }
                    if (remaining.decrementAndGet() == 0) {
                        setStreamingState(false);
                        currentCancel = null;
                    }
                })
            );

            addConsumedTokens(profile, requestTokens);
        }
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
        appendAIResponse(aiResponse, "AI");
    }

    private void appendAIResponse(String aiResponse, String label) {
        // Filtrar action e instrucciones internas para el modelo
        String userVisible = extractUserVisible(aiResponse);
        if (userVisible.isBlank()) {
            // No mostrar nada si solo hay action/instrucción
            return;
        }
        // Línea en blanco antes del globo (solo uno)
        chatArea.append("\n");
        int start = chatArea.getCharCount();
        String aiLabel = (label == null || label.isBlank()) ? "AI:" : "AI (" + label + "):";
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
        ChatSession leader = resolveSession(ChatProfile.TEAM_LEADER, providerCombo.getText(), modelCombo.getText(), project);
        leader.loadHistory();
        chatHistory.clear();
        chatHistory.addAll(leader.getHistory());
        resetViewMessages();
        for (ChatMessage msg : leader.getHistory()) {
            recordLoadedMessageForViews(ChatProfile.TEAM_LEADER, msg);
        }
        renderView();
        refreshTotalTokenCount();
    }
    
    private void showErrorLog() {
        MessageDialog.openInformation(getSite().getShell(), "Errors", String.join("\n", errorLog));
    }

    private void cancelStreaming() {
        if (currentCancel != null) {
            currentCancel.run();
        }
        currentCancel = null;
        statusInfo("Respuesta cancelada");
        setStreamingState(false);
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

    private void addConsumedTokens(ChatProfile profile, int tokens) {
        if (profile == null || tokens <= 0) {
            return;
        }
        consumedTokens.merge(profile, tokens, Integer::sum);
        refreshTotalTokenCount();
    }

    private void clearConsumedTokens(ChatProfile profile) {
        if (profile == null) {
            return;
        }
        consumedTokens.remove(profile);
    }

    private void refreshTotalTokenCount() {
        if (progressText != null && !progressText.isDisposed()) {
            progressText.setText("Tokens total: " + scopedConsumedTokens());
        }
    }

    private int scopedConsumedTokens() {
        if (isTeamMode()) {
            int total = 0;
            for (ChatProfile profile : ChatProfile.values()) {
                total += consumedTokens.getOrDefault(profile, 0);
            }
            return total;
        }
        return consumedTokens.getOrDefault(ChatProfile.TEAM_LEADER, 0);
    }

    private int estimateRequestTokens(ChatSession session, String prompt) {
        if (contextBuilder == null || session == null) {
            return estimateTokens(prompt);
        }

        List<ChatMessage> requestHistory = new ArrayList<>(session.getHistory());
        String effectivePrompt = prompt;

        if (prompt != null && prompt.startsWith(ACTION_RESULT_PREFIX)) {
            requestHistory.add(new ChatMessage("tool", prompt));
            effectivePrompt = ACTION_FOLLOW_UP_PROMPT;
        }

        String context = contextBuilder.buildContext(requestHistory);
        return estimateTokens(context) + estimateTokens(effectivePrompt);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int codePoints = text.codePointCount(0, text.length());
        return Math.max(1, (codePoints + 3) / 4);
    }

    private void setStreamingState(boolean streaming) {
        if (spinnerButton != null && !spinnerButton.isDisposed()) {
            spinnerButton.setEnabled(streaming);
            spinnerButton.setText(streaming ? "⟳ (ON)" : "⟳");
        }
        if (stopButton != null && !stopButton.isDisposed()) {
            stopButton.setEnabled(streaming);
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

    private void continueAutomatedConversation(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        sendSingleMessage(msg, workspaceService.getActiveProjectName(), false);
    }

    private ChatSession resolveSession(ChatProfile profile, String fallbackProvider, String fallbackModel, String projectName) {
        ChatSession session = sessions.computeIfAbsent(profile, p ->
                new ChatSession(p, new ChatController(new ChatHistoryStore(), workspaceService), contextBuilder));

        String provider = (profile == ChatProfile.TEAM_LEADER)
                ? fallbackProvider
                : profileConfigService.getProvider(profile);

        if (provider == null || provider.isBlank()) {
            provider = fallbackProvider;
        }

        AiChatService service = createService(provider);
        if (service != null) {
            String model = (profile == ChatProfile.TEAM_LEADER)
                    ? fallbackModel
                    : profileConfigService.getModel(profile);
            if (model == null || model.isBlank()) {
                model = fallbackModel;
            }
            if (model != null && !model.isBlank()) {
                service.setModel(model);
            }
            session.setAiService(service);
        }

        session.setProjectKey(buildProjectKey(projectName, profile));
        return session;
    }

    private AiChatService createService(String provider) {
        String name = provider == null ? "" : provider.trim();
        return switch (name) {
            case "OpenAI" -> new OpenAiChatService();
            case "Gemini" -> new GeminiChatService();
            case "Qwen" -> new QwenChatService();
            case "DeepSeek" -> new DeepSeekChatService();
            default -> new OllamaChatService();
        };
    }

    private String buildProjectKey(String projectName, ChatProfile profile) {
        String base = projectName == null ? "" : projectName.trim();
        if (profile == ChatProfile.TEAM_LEADER || base.isBlank()) {
            return base;
        }
        return base + "::" + profile.name();
    }

    private boolean isTeamMode() {
        return teamModeButton != null && !teamModeButton.isDisposed() && teamModeButton.getSelection();
    }

    private void resetViewMessages() {
        viewAllMessages.clear();
        for (ChatProfile profile : ChatProfile.values()) {
            List<ChatMessage> list = viewMessages.get(profile);
            if (list != null) {
                list.clear();
            }
        }
    }

    private void recordUserMessageForViews(String msg) {
        recordUserMessageForViews(msg, isTeamMode());
    }

    private void recordUserMessageForViews(String msg, boolean allProfiles) {
        if (msg == null) return;
        viewAllMessages.add(new ChatMessage("user", msg));
        if (allProfiles) {
            for (ChatProfile profile : ChatProfile.values()) {
                viewMessages.get(profile).add(new ChatMessage("user", msg));
            }
        } else {
            viewMessages.get(ChatProfile.TEAM_LEADER).add(new ChatMessage("user", msg));
        }
    }

    private void recordAssistantMessageForViews(ChatProfile profile, String label, String msg) {
        if (msg == null || msg.isBlank()) return;
        String resolvedLabel = (label == null || label.isBlank()) ? profile.getDisplayName() : label;
        viewAllMessages.add(new ChatMessage("assistant", "[" + resolvedLabel + "] " + msg));
        viewMessages.get(profile).add(new ChatMessage("assistant", msg));
    }

    private void recordLocalAssistantMessageForViews(String msg, boolean allProfiles) {
        if (msg == null || msg.isBlank()) return;
        viewAllMessages.add(new ChatMessage("assistant", "[Local] " + msg));
        if (allProfiles) {
            for (ChatProfile profile : ChatProfile.values()) {
                viewMessages.get(profile).add(new ChatMessage("assistant", msg));
            }
        } else {
            viewMessages.get(ChatProfile.TEAM_LEADER).add(new ChatMessage("assistant", msg));
        }
    }

    private void recordLoadedMessageForViews(ChatProfile profile, ChatMessage msg) {
        if (msg == null) return;
        String role = msg.getRole() == null ? "" : msg.getRole().toLowerCase();
        if ("assistant".equals(role)) {
            recordAssistantMessageForViews(profile, "AI", msg.getContent());
        } else if ("user".equals(role)) {
            recordUserMessageForViews(msg.getContent(), false);
        }
    }

    private boolean shouldRenderForProfile(ChatProfile profile) {
        if (viewCombo == null || viewCombo.isDisposed()) {
            return true;
        }
        if (viewCombo.getSelectionIndex() == 0) {
            return true;
        }
        ChatProfile selected = selectedViewProfile();
        return selected == null || selected == profile;
    }

    private void renderView() {
        if (chatArea == null || chatArea.isDisposed()) return;
        chatArea.setText("");
        List<ChatMessage> messages = resolveViewMessages();
        if (messages == null || messages.isEmpty()) {
            appendSystem("Sin mensajes en este chat");
            return;
        }
        for (ChatMessage msg : messages) {
            appendMessage(msg.getRole(), msg.getContent());
        }
    }

    private List<ChatMessage> resolveViewMessages() {
        if (viewCombo == null || viewCombo.isDisposed() || viewCombo.getSelectionIndex() == 0) {
            return viewAllMessages;
        }
        ChatProfile selected = selectedViewProfile();
        if (selected == null) {
            return viewAllMessages;
        }
        return viewMessages.getOrDefault(selected, viewAllMessages);
    }

    private ChatProfile selectedViewProfile() {
        if (viewCombo == null || viewCombo.isDisposed()) return null;
        String text = viewCombo.getText();
        if ("Team Leader".equalsIgnoreCase(text)) return ChatProfile.TEAM_LEADER;
        if ("Dev Senior".equalsIgnoreCase(text)) return ChatProfile.DEV_SENIOR;
        if ("Arquitecto Senior".equalsIgnoreCase(text)) return ChatProfile.ARQ_SENIOR;
        if ("Auditor Senior".equalsIgnoreCase(text)) return ChatProfile.AUDITOR_SENIOR;
        return null;
    }

    private void clearHistories() {
        String project = workspaceService.getActiveProjectName();
        if (isTeamMode()) {
            for (ChatProfile profile : ChatProfile.values()) {
                ChatSession session = resolveSession(profile, providerCombo.getText(), modelCombo.getText(), project);
                session.clearHistory();
                clearConsumedTokens(profile);
            }
        } else {
            ChatSession leader = resolveSession(ChatProfile.TEAM_LEADER, providerCombo.getText(), modelCombo.getText(), project);
            leader.clearHistory();
            clearConsumedTokens(ChatProfile.TEAM_LEADER);
        }
        resetViewMessages();
        renderView();
        refreshTotalTokenCount();
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