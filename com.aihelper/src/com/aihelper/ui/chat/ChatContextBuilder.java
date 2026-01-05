package com.aihelper.ui.chat;

import java.util.List;

import com.aihelper.model.ChatMessage;
import com.aihelper.workspace.WorkspaceService;

/**
 * Construye el contexto enviado al servicio de IA en base al historial
 * de mensajes y al estado del workspace.
 */
public class ChatContextBuilder {

        private static final String TEMPLATE = """
            CHAT HISTORY:
            %s

            Active file: %s
            Language: %s

            Code:
            %s

            INSTRUCTIONS:
            You are AI Helper Chat inside Eclipse. Use only user-provided or workspace-provided information, and only when the user asks for it. To request more context you have special actions that must appear alone on their own line (no bullets, no extra text):

            Action syntax (choose one): plain params, quoted params, or JSON on the same line:
            - [ACTION:READ_FILE] project=com.aihelper path=src/com/aihelper/ui/ChatView.java
            - [ACTION:READ_FILE] project="com.aihelper" path="src/com/aihelper/ui/ChatView.java"
            - [ACTION:READ_FILE]{"project":"com.aihelper","path":"src/com/aihelper/ui/ChatView.java"}

            1) [ACTION:READ_FILE] project=<projectName> path=<project/relative/path>
            - Use only when you need the full contents of an existing file.
            - `project` must exactly match the PDE project name (e.g., com.aihelper).
            - `path` is relative to that project root, e.g., src/com/aihelper/ui/ChatView.java.

            1b) [ACTION:READ_FILE_RANGE] project=<projectName> path=<path> start=<line> end=<line>
            - Use when you only need a slice of a file (1-based, inclusive).

            1c) [ACTION:READ_ACTIVE_FILE]
            - Returns the current editor contents (includes unsaved changes).

            1d) [ACTION:READ_ACTIVE_SELECTION]
            - Returns only the current text selection in the active editor.

            2) [ACTION:READ_PROJECT]
            - Requests a summarized workspace snapshot (projects list, relevant directory trees, and open files).
            - Use only when you truly need a global view before asking for specifics.

            3) [ACTION:LIST_FILES] project=<projectName> depth=<n?> limit=<m?>
            - Retrieves only the folder/file structure for the given project.
            - `depth` and `limit` are optional (defaults: depth=2, limit=200) to avoid huge responses.

            4) [ACTION:LIST_OPEN_FILES]
            - Returns names and project-relative paths of currently open editors.

            5) [ACTION:SEARCH_TEXT] project=<projectName> query=<text> limit=<n?>
            - Case-insensitive search; returns file paths (and first matching line) up to a safe limit (default 50).

            Rules:
            - Ask for only one action per message; wait for the system to reply with that content before continuing.
            - If you lack needed information, say what is missing and propose the single best action instead of inventing details.
            - Never assume you have seen a file unless you requested it in this conversation.
            - After receiving requested info, incorporate it; do not repeat the action unless there is a new, clear reason.
            - Response language: detect the user’s language from recent messages. If you can respond in that language, do so; otherwise, respond in English.
            - Formatting: when you show code or file contents, use ONE Markdown fenced code block with real newlines (no literal "\n" sequences); do not escape content, and avoid preambles like "Here is the code".
            - Use a language tag on the fence (e.g., ```java). Do NOT wrap the fence itself in backticks. Do NOT use inline single backticks for multi-line code. Preserve backslashes and quotes exactly as in the source so they render and highlight correctly.
            - Do not invent actions or parameters beyond the list above; if a desired action is missing, ask for clarification instead of creating a new tag.
            End of context.
            """;

    private final WorkspaceService workspaceService;

    public ChatContextBuilder(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public String buildContext(List<ChatMessage> history) {
        return TEMPLATE.formatted(
            formatHistory(history),
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
