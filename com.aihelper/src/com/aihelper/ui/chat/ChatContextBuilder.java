package com.aihelper.ui.chat;

import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import com.aihelper.Activator;
import com.aihelper.model.ChatMessage;
import com.aihelper.preferences.PreferenceConstants;
import com.aihelper.workspace.WorkspaceService;

/**
 * Construye el contexto enviado al servicio de IA en base al historial
 * de mensajes y al estado del workspace.
 */
public class ChatContextBuilder {

        private static final String TEMPLATE = """
            Chat History:
            %s

            Active file: %s
            Language: %s

            Code:
            %s

            Instructions:
            You are an assistant inside Eclipse. The user will only interact with you in natural language. If you need information or an operation from the system, you can use special commands called [ACTION:...], but these are only for you, not for the user. Never mention, show, or explain these actions to the user. Never tell the user to use them. Only use them internally to get information or perform actions, and always reply to the user in natural language as if you were a human assistant. If you ever mention or explain an action to the user, your answer will be ignored.

            Here is how you can use actions (choose one syntax: plain, quoted, or JSON):
            - [ACTION:READ_FILE] project=com.aihelper path=src/com/aihelper/ui/ChatView.java
            - [ACTION:READ_FILE] project=\"com.aihelper\" path=\"src/com/aihelper/ui/ChatView.java\"
            - [ACTION:READ_FILE]{\"project\":\"com.aihelper\",\"path\":\"src/com/aihelper/ui/ChatView.java\"}

            Available actions:
            1) [ACTION:READ_FILE] project=<projectName> path=<project/relative/path>
            Use this when you need the full contents of a file. The project name must match exactly. The path is relative to the project root.

            1b) [ACTION:READ_FILE_RANGE] project=<projectName> path=<path> start=<line> end=<line>
            Use this when you only need part of a file (lines are 1-based, inclusive).

            1c) [ACTION:READ_ACTIVE_FILE]
            Use this to get the current editor contents, including unsaved changes.

            1d) [ACTION:READ_ACTIVE_SELECTION]
            Use this to get only the current text selection in the active editor.

            2) [ACTION:READ_PROJECT]
            Use this to get a summary of the workspace: projects, directory trees, and open files.

            2b) [ACTION:READ_PROJECT_FULL] project=<projectName>
            Use this to get a complete list of all files and their relative paths in the project.

            3) [ACTION:LIST_FILES] project=<projectName> depth=<n?> limit=<m?>
            Use this to get the folder/file structure for a project. Depth and limit are optional (defaults: depth=2, limit=200).

            4) [ACTION:LIST_OPEN_FILES]
            Use this to get the names and paths of currently open editors.

            5) [ACTION:SEARCH_TEXT] project=<projectName> query=<text> limit=<n?>
            Use this to search for text in files. The search is case-insensitive and returns file paths and the first matching line, up to a safe limit (default 50).

            Rules:
            - Only use one action per message. Wait for the system to reply before continuing.
            - If you are missing information, say what is missing and propose the best action, but do not invent details.
            - Never assume you have seen a file unless you requested it in this conversation.
            - After receiving information, use it and do not repeat the action unless there is a new reason.
            - When you show code or file contents, use a single Markdown fenced code block with real newlines. Do not escape content or add preambles like "Here is the code". Use a language tag on the fence (for example, ```java). Do not use inline single backticks for multi-line code. Preserve backslashes and quotes exactly as in the source.
            - Do not invent actions or parameters beyond the list above. If you need something not listed, ask for clarification.
            - Always reply to the user in natural language, never mentioning actions.
            End of context.
            """;

    private final WorkspaceService workspaceService;

    public ChatContextBuilder(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public String buildContext(List<ChatMessage> history) {
        int maxHistory = 50;
        try {
            IPreferenceStore store = Activator.getDefault() != null ? Activator.getDefault().getPreferenceStore() : null;
            if (store != null) {
                maxHistory = store.getInt(PreferenceConstants.CHAT_MAX_HISTORY);
            }
        } catch (Exception e) {
            // fallback to default
        }
        List<ChatMessage> limitedHistory = history;
        if (history != null && history.size() > maxHistory) {
            limitedHistory = history.subList(history.size() - maxHistory, history.size());
        }
        return TEMPLATE.formatted(
            formatHistory(limitedHistory),
            workspaceService.getActiveEditorFileName(),
            workspaceService.getActiveEditorFileExtension(),
            workspaceService.getActiveEditorContent()
        );
    }

    private String formatHistory(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        if (history == null) {
            return sb.toString();
        }

        for (ChatMessage message : history) {
            sb.append(message.getRole().toUpperCase())
              .append(":\n")
              .append(message.getContent())
              .append("\n\n");
        }
        return sb.toString();
    }
}