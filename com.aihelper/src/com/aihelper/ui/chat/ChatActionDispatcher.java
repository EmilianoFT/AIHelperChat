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

    private static final Pattern READ_FILE_KV = Pattern.compile(
        "project=([^\\s\"]+|\"[^\"]+\")\\s+path=([^\\s\"]+|\"[^\"]+\")",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_FILE_JSON = Pattern.compile(
        "\\{[^}]*\"project\"\\s*:\\s*\"([^\"]+)\"[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}",
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
        "\\{[^}]*\"project\"\\s*:\\s*\"([^\"]+)\"[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\"start\"\\s*:\\s*(\\d+)\s*[^}]*\"end\"\\s*:\\s*(\\d+)\s*[^}]*\\}",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_ACTIVE_FILE_PATTERN = Pattern.compile("\\[ACTION:READ_ACTIVE_FILE\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_ACTIVE_SELECTION_PATTERN = Pattern.compile("\\[ACTION:READ_ACTIVE_SELECTION\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_OPEN_FILES_PATTERN = Pattern.compile("\\[ACTION:LIST_OPEN_FILES\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_PROJECT_PATTERN = Pattern.compile("\\[ACTION:READ_PROJECT\\]", Pattern.CASE_INSENSITIVE);

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
            this::handleListOpenFiles
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
            automatedSender.accept("[SYSTEM] Archivo no encontrado o vacío: " + path);
            return true;
        }

        automatedSender.accept("Contenido solicitado:\n" + content);
        return true;
    }

    private boolean handleReadProject(String text) {
        if (!READ_PROJECT_PATTERN.matcher(text).find()) {
            return false;
        }
        automatedSender.accept("Snapshot del proyecto:\n" +
            workspaceService.readWorkspaceSnapshot());
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

        automatedSender.accept("Listado de archivos:\n" +
            workspaceService.listProjectTree(project, depth, limit));
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
        automatedSender.accept("Búsqueda:\n" + workspaceService.searchText(project, query, limit));
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
            automatedSender.accept("[SYSTEM] Rango vacío o archivo no encontrado: " + path);
            return true;
        }
        automatedSender.accept("Contenido solicitado (" + start + "-" + end + "):\n" + content);
        return true;
    }

    private boolean handleReadActiveFile(String text) {
        if (!READ_ACTIVE_FILE_PATTERN.matcher(text).find()) {
            return false;
        }
        String content = workspaceService.getActiveEditorContent();
        if (content == null || content.isBlank()) {
            automatedSender.accept("[SYSTEM] No hay editor activo o está vacío");
        } else {
            automatedSender.accept("Archivo activo:\n" + content);
        }
        return true;
    }

    private boolean handleReadActiveSelection(String text) {
        if (!READ_ACTIVE_SELECTION_PATTERN.matcher(text).find()) {
            return false;
        }
        String sel = workspaceService.getActiveSelectionText();
        if (sel == null || sel.isBlank()) {
            automatedSender.accept("[SYSTEM] No hay selección activa");
        } else {
            automatedSender.accept("Selección activa:\n" + sel);
        }
        return true;
    }

    private boolean handleListOpenFiles(String text) {
        if (!LIST_OPEN_FILES_PATTERN.matcher(text).find()) {
            return false;
        }
        automatedSender.accept("Archivos abiertos:\n" + workspaceService.listOpenFiles());
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

    @FunctionalInterface
    private interface ActionHandler {
        boolean tryHandle(String text);
    }
}
