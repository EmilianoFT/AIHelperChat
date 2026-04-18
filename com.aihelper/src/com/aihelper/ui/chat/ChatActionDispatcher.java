package com.aihelper.ui.chat;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.preference.IPreferenceStore;

import com.aihelper.preferences.CredentialsService;
import com.aihelper.preferences.PreferenceConstants;
import com.aihelper.workspace.WorkspaceService;

/**
 * Encapsula la detección y ejecución de acciones especiales pedidas por la IA.
 */
public class ChatActionDispatcher {

    private static final int ACTION_RESULT_CHAR_LIMIT = 4000;

    private static final Pattern READ_FILE_KV = Pattern.compile(
        "\\[ACTION:READ_FILE\\]\\s*project=([^\\s\"]+|\"[^\"]+\")\\s+path=([^\\s\"]+|\"[^\"]+\")",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_FILE_JSON = Pattern.compile(
        "\\[ACTION:READ_FILE\\]\\s*\\{[^}]*\"project\"\\s*:\\s*\"([^\"]+)\"[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LIST_FILES_PATTERN = Pattern.compile(
        "\\[ACTION:LIST_FILES\\]\\s*project=([^\\s\"]+|\"[^\"]+\")(?:\\s+depth=(\\d+))?(?:\\s+limit=(\\d+))?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SEARCH_TEXT_PATTERN = Pattern.compile(
        "\\[ACTION:SEARCH_TEXT\\]\\s*project=([^\\s\"]+|\"[^\"]+\")\\s+query=([^\\s\"]+|\"[^\"]+\")(?:\\s+limit=(\\d+))?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_FILE_RANGE_PATTERN = Pattern.compile(
        "\\[ACTION:READ_FILE_RANGE\\].*project=([^\\s\"]+|\"[^\"]+\")\\s+path=([^\\s\"]+|\"[^\"]+\")\\s+start=(\\d+)\\s+end=(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_FILE_RANGE_JSON = Pattern.compile(
        "\\[ACTION:READ_FILE_RANGE\\]\\s*\\{[^}]*\"project\"\\s*:\\s*\"([^\"]+)\"[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\"start\"\\s*:\\s*(\\d+)\\s*[^}]*\"end\"\\s*:\\s*(\\d+)\\s*[^}]*\\}",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_ACTIVE_FILE_PATTERN = Pattern.compile("\\[ACTION:READ_ACTIVE_FILE\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_ACTIVE_SELECTION_PATTERN = Pattern.compile("\\[ACTION:READ_ACTIVE_SELECTION\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_OPEN_FILES_PATTERN = Pattern.compile("\\[ACTION:LIST_OPEN_FILES\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_PROJECT_PATTERN = Pattern.compile("\\[ACTION:READ_PROJECT\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_PROJECT_FULL_PATTERN = Pattern.compile("\\[ACTION:READ_PROJECT_FULL\\]\\s*project=([^\\s\"]+|\"[^\"]+\")", Pattern.CASE_INSENSITIVE);

    private final WorkspaceService workspaceService;
    private final Consumer<String> automatedSender;
    private final IPreferenceStore preferenceStore = CredentialsService.preferenceStore();
    private final List<ActionHandler> handlers;

    public ChatActionDispatcher(WorkspaceService workspaceService, Consumer<String> automatedSender) {
        this.workspaceService = workspaceService;
        this.automatedSender = automatedSender;
        this.handlers = List.of(
            this::handleReadFileRange,
            this::handleReadFile,
            this::handleReadActiveFile,
            this::handleReadActiveSelection,
            this::handleReadProject,
            this::handleListFiles,
            this::handleSearchText,
            this::handleListOpenFiles,
            this::handleReadProjectFull // Nuevo handler
        );
    }

    public void handle(String assistantResponse) {
        if (assistantResponse == null || assistantResponse.isBlank()) {
            return;
        }
        for (ActionHandler handler : handlers) {
            if (handler.tryHandle(assistantResponse)) {
                return;
            }
        }
    }

    private String nextStepMessage(String actionResult) {
        return actionResult +
            "\n\nUsa este resultado. Si todavía necesitas información, responde con exactamente una acción. Si ya puedes responder, contesta al usuario directamente y no expliques las acciones.";
    }

    private boolean handleReadFile(String text) {
        String project = null;
        String path = null;

        Matcher json = READ_FILE_JSON.matcher(text);
        if (json.find()) {
            project = json.group(1);
            path = json.group(2);
        } else {
            Matcher kv = READ_FILE_KV.matcher(text);
            if (kv.find()) {
                project = stripQuotes(kv.group(1));
                path = stripQuotes(kv.group(2));
            }
        }

        if (project == null || path == null) {
            return false;
        }

        String content = workspaceService.readFile(project, path);
        if (content == null || content.isBlank()) {
            automatedSender.accept(nextStepMessage(buildMissingResult("READ_FILE", project, path, "Archivo no encontrado o vacío")));
            return true;
        }

        automatedSender.accept(nextStepMessage(buildFileResult("READ_FILE", project, path, null, null, content)));
        return true;
    }

    private boolean handleReadProject(String text) {
        if (!READ_PROJECT_PATTERN.matcher(text).find()) {
            return false;
        }
        automatedSender.accept(nextStepMessage("Snapshot del proyecto:\n" + workspaceService.readWorkspaceSnapshot()));
        return true;
    }

    private boolean handleListFiles(String text) {
        Matcher matcher = LIST_FILES_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }

        String project = stripQuotes(matcher.group(1));
        int depth = Math.min(resolveMaxDepth(), parseOrDefault(matcher.group(2), 2));
        int limit = Math.min(resolveMaxLimit(), parseOrDefault(matcher.group(3), 200));

        automatedSender.accept(nextStepMessage("Listado de archivos:\n" + workspaceService.listProjectTree(project, depth, limit)));
        return true;
    }

    private boolean handleSearchText(String text) {
        Matcher matcher = SEARCH_TEXT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        String project = stripQuotes(matcher.group(1));
        String query = stripQuotes(matcher.group(2));
        int limit = Math.min(resolveMaxLimit(), parseOrDefault(matcher.group(3), 50));
        automatedSender.accept(nextStepMessage("Búsqueda:\n" + workspaceService.searchText(project, query, limit)));
        return true;
    }

    private boolean handleReadFileRange(String text) {
        Matcher json = READ_FILE_RANGE_JSON.matcher(text);
        if (json.find()) {
            return dispatchFileRange(stripQuotes(json.group(1)), stripQuotes(json.group(2)), json.group(3), json.group(4));
        }

        Matcher matcher = READ_FILE_RANGE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        return dispatchFileRange(stripQuotes(matcher.group(1)), stripQuotes(matcher.group(2)), matcher.group(3), matcher.group(4));
    }

    private boolean dispatchFileRange(String project, String path, String startStr, String endStr) {
        int start = parseOrDefault(startStr, 1);
        int end = parseOrDefault(endStr, start + 200);
        String content = workspaceService.readFileRange(project, path, start, end);
        if (content == null || content.isBlank()) {
            automatedSender.accept(nextStepMessage(buildMissingResult("READ_FILE_RANGE", project, path, "Rango vacío o archivo no encontrado")));
            return true;
        }
        automatedSender.accept(nextStepMessage(buildFileResult("READ_FILE_RANGE", project, path, start, end, content)));
        return true;
    }

    private boolean handleReadActiveFile(String text) {
        if (!READ_ACTIVE_FILE_PATTERN.matcher(text).find()) {
            return false;
        }
        String content = workspaceService.getActiveEditorContent();
        if (content == null || content.isBlank()) {
            automatedSender.accept(nextStepMessage("[SYSTEM] No hay editor activo o está vacío"));
        } else {
            automatedSender.accept(nextStepMessage("Archivo activo:\n" + content));
        }
        return true;
    }

    private boolean handleReadActiveSelection(String text) {
        if (!READ_ACTIVE_SELECTION_PATTERN.matcher(text).find()) {
            return false;
        }
        String sel = workspaceService.getActiveSelectionText();
        if (sel == null || sel.isBlank()) {
            automatedSender.accept(nextStepMessage("[SYSTEM] No hay selección activa"));
        } else {
            automatedSender.accept(nextStepMessage("Selección activa:\n" + sel));
        }
        return true;
    }

    private boolean handleListOpenFiles(String text) {
        if (!LIST_OPEN_FILES_PATTERN.matcher(text).find()) {
            return false;
        }
        automatedSender.accept(nextStepMessage("Archivos abiertos:\n" + workspaceService.listOpenFiles()));
        return true;
    }

    private boolean handleReadProjectFull(String text) {
        Matcher matcher = READ_PROJECT_FULL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        String project = stripQuotes(matcher.group(1));
        String listing = workspaceService.listAllFilesRecursive(project);
        automatedSender.accept(nextStepMessage("Listado completo de archivos del proyecto '" + project + "':\n" + listing));
        return true;
    }

    private int resolveMaxDepth() {
        return clamp(preferenceStore.getInt(PreferenceConstants.LIST_MAX_DEPTH), 1, 10);
    }

    private int resolveMaxLimit() {
        return clamp(preferenceStore.getInt(PreferenceConstants.LIST_MAX_LIMIT), 10, 1000);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private int parseOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String stripQuotes(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String buildMissingResult(String action, String project, String path, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ACTION_RESULT:").append(action).append("]\n")
          .append("status=ERROR\n")
          .append("project=").append(project).append("\n")
          .append("path=").append(path).append("\n")
          .append("message=").append(reason).append("\n")
          .append("[/ACTION_RESULT]");
        return sb.toString();
    }

    private String buildFileResult(String action, String project, String path, Integer start, Integer end, String content) {
        String language = inferLanguage(path);
                boolean truncated = content != null && content.length() > ACTION_RESULT_CHAR_LIMIT;
                String normalized = trimActionContent(content);
        StringBuilder sb = new StringBuilder();
        sb.append("[ACTION_RESULT:").append(action).append("]\n")
          .append("status=OK\n")
          .append("project=").append(project).append("\n")
          .append("path=").append(path).append("\n");
        if (start != null && end != null) {
            sb.append("range=").append(start).append("-").append(end).append("\n");
        }
                sb.append("truncated=").append(truncated).append("\n");
        sb.append("language=").append(language).append("\n")
          .append("content:\n")
          .append("```").append(language).append("\n")
          .append(normalized);
        if (!normalized.endsWith("\n")) {
            sb.append("\n");
        }
                sb.append("```\n");
                if (truncated) {
                        sb.append("note=Use READ_FILE_RANGE if you need a specific section of this file.\n");
                }
                sb
          .append("[/ACTION_RESULT]");
        return sb.toString();
    }

    private String trimActionContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= ACTION_RESULT_CHAR_LIMIT) {
            return content;
        }
        return content.substring(0, ACTION_RESULT_CHAR_LIMIT) + "\n... [truncated]";
    }

    private String inferLanguage(String path) {
        if (path == null || path.isBlank()) {
            return "text";
        }
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "text";
        }
        return path.substring(idx + 1).toLowerCase();
    }

    @FunctionalInterface
    private interface ActionHandler {
        boolean tryHandle(String text);
    }
}