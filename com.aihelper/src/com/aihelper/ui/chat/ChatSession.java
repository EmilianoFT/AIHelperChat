package com.aihelper.ui.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.aihelper.ai.AiChatService;
import com.aihelper.model.ChatMessage;

public class ChatSession {

    private static final String ACTION_RESULT_PREFIX = "[ACTION_RESULT:";
    private static final String ACTION_FOLLOW_UP_PROMPT =
            "Use the latest ACTION_RESULT from the conversation history. If more data is needed, output exactly one action line. Otherwise answer the user directly.";

    private final ChatProfile profile;
    private final ChatController controller;
    private final ChatContextBuilder contextBuilder;
    private final List<ChatMessage> history = new ArrayList<>();
    private final StringBuilder responseBuffer = new StringBuilder();

    private AiChatService aiService;
    private String projectKey;
    private Runnable currentCancel;

    public ChatSession(ChatProfile profile, ChatController controller, ChatContextBuilder contextBuilder) {
        this.profile = profile;
        this.controller = controller;
        this.contextBuilder = contextBuilder;
    }

    public ChatProfile getProfile() {
        return profile;
    }

    public void setAiService(AiChatService aiService) {
        this.aiService = aiService;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void loadHistory() {
        if (projectKey == null || controller == null) return;
        List<ChatMessage> loaded = controller.loadHistory(projectKey);
        history.clear();
        history.addAll(loaded);
    }

    public void clearHistory() {
        history.clear();
        if (projectKey != null) {
            controller.clearHistory(projectKey);
        }
    }

    public void appendMessage(String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        ChatMessage message = new ChatMessage(role, content);
        history.add(message);
        if (projectKey != null) {
            controller.append(message, projectKey);
        }
    }

    public Runnable sendMessageStreaming(
            String prompt,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Consumer<String> onComplete) {

        if (aiService == null) {
            onError.accept(new IllegalStateException("Servicio no configurado para " + profile.getDisplayName()));
            onComplete.accept("");
            return () -> {};
        }

        responseBuffer.setLength(0);
        boolean actionResultPrompt = prompt != null && prompt.startsWith(ACTION_RESULT_PREFIX);
        String effectivePrompt = prompt;

        if (actionResultPrompt) {
            ChatMessage toolMsg = new ChatMessage("tool", prompt);
            history.add(toolMsg);
            if (projectKey != null) {
                controller.append(toolMsg, projectKey);
            }
            effectivePrompt = ACTION_FOLLOW_UP_PROMPT;
        } else {
            ChatMessage userMsg = new ChatMessage("user", prompt);
            history.add(userMsg);
            if (projectKey != null) {
                controller.append(userMsg, projectKey);
            }
        }

        String context = contextBuilder.buildContext(history);

        currentCancel = aiService.sendMessageStreaming(
            effectivePrompt,
            context,
            chunk -> {
                if (chunk != null) {
                    responseBuffer.append(chunk);
                    onChunk.accept(chunk);
                }
            },
            err -> {
                onError.accept(err);
                currentCancel = null;
            },
            () -> {
                String response = responseBuffer.toString();
                if (!response.isEmpty()) {
                    ChatMessage assistant = new ChatMessage("assistant", response);
                    history.add(assistant);
                    if (projectKey != null) {
                        controller.append(assistant, projectKey);
                    }
                }
                onComplete.accept(response);
                currentCancel = null;
            }
        );

        return currentCancel;
    }

    public void cancel() {
        if (currentCancel != null) {
            currentCancel.run();
        }
        currentCancel = null;
    }
}
