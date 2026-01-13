package com.aihelper.ui.chat;

import java.util.ArrayList;
import java.util.List;

import com.aihelper.model.ChatMessage;
import com.aihelper.workspace.WorkspaceService;

public class ChatController {

    private static final int HISTORY_LIMIT = 200;

    private final ChatHistoryStore historyStore;
    private final WorkspaceService workspaceService;

    private final List<ChatMessage> sessionHistory = new ArrayList<>();

    public ChatController(
            ChatHistoryStore historyStore,
            WorkspaceService workspaceService) {

        this.historyStore = historyStore;
        this.workspaceService = workspaceService;
    }

    public List<ChatMessage> loadHistory(String projectName) {
        List<ChatMessage> loaded =
                historyStore.load(projectName, HISTORY_LIMIT);

        sessionHistory.clear();
        sessionHistory.addAll(loaded);
        return loaded;
    }

    public void append(ChatMessage msg, String projectName) {
        sessionHistory.add(msg);
        historyStore.save(projectName, sessionHistory, HISTORY_LIMIT);
    }

    public void applyCodeToActiveEditor(String code) {
        workspaceService.applyFullToActiveEditor(code);
    }

    public void clearHistory(String projectName) {
        sessionHistory.clear();
        historyStore.clear(projectName);
    }
}

