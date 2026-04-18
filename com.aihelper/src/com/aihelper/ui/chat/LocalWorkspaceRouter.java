package com.aihelper.ui.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.aihelper.workspace.WorkspaceService;

public class LocalWorkspaceRouter {

    private static final Pattern JAVA_METHOD_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private)?\\s*(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+|default\\s+|native\\s+)*[\\w$<>\\[\\], ?]+\\s+(\\w+)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{]+)?\\{");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("([A-Za-z0-9_./\\\\-]+\\.java)");
    private static final Pattern DOTTED_NAME_PATTERN = Pattern.compile("\\b([a-z][\\w]*(?:\\.[a-zA-Z_][\\w]*)+)\\b");
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");

    private static final int MAX_FILE_RESPONSE_CHARS = 8000;

    private final WorkspaceService workspaceService;

    public LocalWorkspaceRouter(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public String tryHandle(String prompt, String fallbackProjectName) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }

        String normalized = normalize(prompt);
        String projectName = resolveProjectName(prompt, fallbackProjectName);

        if (asksCapabilities(normalized)) {
            return supportedCommands();
        }
        if (asksProjectListing(normalized)) {
            return listProjectFiles(projectName);
        }
        if (asksProjectStructure(normalized)) {
            return projectStructure(projectName);
        }

        String fileHint = extractFileHint(prompt);
        if (fileHint == null) {
            return null;
        }

        if (asksFunctionList(normalized)) {
            return listFunctions(projectName, fileHint);
        }
        if (asksReadFile(normalized)) {
            return readFileContent(projectName, fileHint);
        }

        return null;
    }

    private String supportedCommands() {
        return String.join("\n",
                "Puedo resolver localmente estas operaciones del workspace:",
                "- Listar funciones/metodos de una clase Java, por ejemplo ChatView o JsonHelper.",
                "- Leer el contenido de un archivo Java por nombre o ruta.",
                "- Listar rutas y archivos de un proyecto abierto.",
                "- Mostrar una estructura resumida del proyecto.",
                "- Buscar dentro del proyecto cuando la consulta requiere un archivo concreto.");
    }

    private String listProjectFiles(String projectName) {
        String project = safeProject(projectName);
        if (project == null) {
            return "No pude determinar el proyecto. Indica el nombre del proyecto abierto en Eclipse.";
        }

        String listing = workspaceService.listAllFilesRecursive(project);
        if (listing == null || listing.isBlank()) {
            return "No encontré archivos para el proyecto " + project + ".";
        }

        return "Listado de rutas y archivos del proyecto " + project + ":\n```text\n" + listing + "```";
    }

    private String projectStructure(String projectName) {
        String project = safeProject(projectName);
        if (project == null) {
            return "No pude determinar el proyecto. Indica el nombre del proyecto abierto en Eclipse.";
        }

        String tree = workspaceService.listProjectTree(project, 4, 400);
        if (tree == null || tree.isBlank()) {
            return "No encontré estructura para el proyecto " + project + ".";
        }

        return "Estructura del proyecto " + project + ":\n```text\n" + tree + "```";
    }

    private String listFunctions(String projectName, String fileHint) {
        String project = safeProject(projectName);
        if (project == null) {
            return "No pude determinar el proyecto para listar funciones.";
        }

        Resolution resolution = resolveSingleFile(project, fileHint);
        if (resolution.message != null) {
            return resolution.message;
        }

        String content = workspaceService.readFile(project, resolution.path);
        if (content == null || content.isBlank()) {
            return "No pude leer el archivo " + resolution.path + ".";
        }

        List<String> methods = extractMethodNames(content);
        if (methods.isEmpty()) {
            return "No encontré funciones o metodos en " + resolution.path + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Funciones y metodos encontrados en ")
          .append(resolution.path)
          .append(":\n");
        for (String method : methods) {
            sb.append("- ").append(method).append("\n");
        }
        return sb.toString().trim();
    }

    private String readFileContent(String projectName, String fileHint) {
        String project = safeProject(projectName);
        if (project == null) {
            return "No pude determinar el proyecto para leer el archivo.";
        }

        Resolution resolution = resolveSingleFile(project, fileHint);
        if (resolution.message != null) {
            return resolution.message;
        }

        String content = workspaceService.readFile(project, resolution.path);
        if (content == null || content.isBlank()) {
            return "No pude leer el archivo " + resolution.path + ".";
        }

        String normalized = truncate(content, MAX_FILE_RESPONSE_CHARS);
        String language = inferLanguage(resolution.path);
        StringBuilder sb = new StringBuilder();
        sb.append("Contenido de ").append(resolution.path).append(":\n")
          .append("```").append(language).append("\n")
          .append(normalized);
        if (!normalized.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```");
        if (content.length() > MAX_FILE_RESPONSE_CHARS) {
            sb.append("\nEl archivo fue truncado. Si quieres, puedo listar una seccion concreta.");
        }
        return sb.toString();
    }

    private List<String> extractMethodNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = JAVA_METHOD_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }

    private Resolution resolveSingleFile(String projectName, String hint) {
        List<String> candidates = findCandidates(projectName, hint);
        if (candidates.isEmpty()) {
            return Resolution.withMessage("No encontré un archivo que coincida con " + hint + " en el proyecto " + projectName + ".");
        }
        if (candidates.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("Encontré varios archivos que coinciden con ").append(hint).append(":\n");
            for (String candidate : candidates) {
                sb.append("- ").append(candidate).append("\n");
            }
            sb.append("Indica cuál quieres leer exactamente.");
            return Resolution.withMessage(sb.toString().trim());
        }
        return Resolution.withPath(candidates.get(0));
    }

    private List<String> findCandidates(String projectName, String hint) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists() || !project.isOpen()) {
            return List.of();
        }

        Set<String> ranked = new LinkedHashSet<>();
        List<String> allFiles = new ArrayList<>();
        try {
            collectProjectFiles(project, allFiles);
        } catch (CoreException e) {
            return List.of();
        }

        List<String> normalizedHints = normalizeHints(hint);
        for (String normalizedHint : normalizedHints) {
            String lowerHint = normalizedHint.toLowerCase(Locale.ROOT);
            for (String path : allFiles) {
                String lowerPath = path.toLowerCase(Locale.ROOT);
                if (lowerPath.equals(lowerHint) || lowerPath.endsWith("/" + lowerHint)) {
                    ranked.add(path);
                }
            }
            for (String path : allFiles) {
                String lowerPath = path.toLowerCase(Locale.ROOT);
                if (lowerPath.endsWith(lowerHint) || lowerPath.contains(lowerHint)) {
                    ranked.add(path);
                }
            }
        }

        return new ArrayList<>(ranked);
    }

    private void collectProjectFiles(IContainer container, List<String> files) throws CoreException {
        for (IResource resource : container.members()) {
            if (resource instanceof IFile) {
                files.add(resource.getProjectRelativePath().toString().replace('\\', '/'));
            } else if (resource instanceof IContainer) {
                collectProjectFiles((IContainer) resource, files);
            }
        }
    }

    private List<String> normalizeHints(String hint) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (hint == null || hint.isBlank()) {
            return List.of();
        }

        String trimmed = hint.trim().replace('\\', '/');
        hints.add(trimmed);
        if (!trimmed.endsWith(".java") && !trimmed.contains("/")) {
            hints.add(trimmed + ".java");
        }

        if (trimmed.contains(".")) {
            String slashHint = trimmed.replace('.', '/');
            hints.add(slashHint);
            if (!slashHint.endsWith(".java")) {
                hints.add(slashHint + ".java");
            }
            int lastDot = trimmed.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < trimmed.length() - 1) {
                String simple = trimmed.substring(lastDot + 1);
                hints.add(simple);
                if (!simple.endsWith(".java")) {
                    hints.add(simple + ".java");
                }
            }
        }

        return new ArrayList<>(hints);
    }

    private String extractFileHint(String prompt) {
        Matcher pathMatcher = FILE_PATH_PATTERN.matcher(prompt);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher dottedMatcher = DOTTED_NAME_PATTERN.matcher(prompt);
        while (dottedMatcher.find()) {
            String candidate = dottedMatcher.group(1);
            if (candidate.startsWith("com.")) {
                return candidate;
            }
        }

        Matcher classMatcher = CLASS_NAME_PATTERN.matcher(prompt);
        while (classMatcher.find()) {
            String candidate = classMatcher.group(1);
            if (!"You".equals(candidate) && !"AI".equals(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String resolveProjectName(String prompt, String fallbackProjectName) {
        String normalizedPrompt = normalize(prompt);
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            String name = project.getName();
            if (normalizedPrompt.contains(normalize(name))) {
                return name;
            }
        }
        return fallbackProjectName;
    }

    private String safeProject(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return null;
        }
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists() || !project.isOpen()) {
            return null;
        }
        return project.getName();
    }

    private boolean asksFunctionList(String normalizedPrompt) {
        return containsAny(normalizedPrompt, "funcion", "funciones", "metodo", "metodos", "procedimiento", "procedimientos")
                && containsAny(normalizedPrompt, "lista", "listar", "nombres", "nombre");
    }

    private boolean asksReadFile(String normalizedPrompt) {
        return containsAny(normalizedPrompt, "contenido", "lee", "leer", "dame el contenido", "mostrar el contenido", "busca y dame el contenido");
    }

    private boolean asksProjectListing(String normalizedPrompt) {
        return containsAny(normalizedPrompt,
                "lista las rutas y archivos",
                "lista rutas y archivos",
                "lista todos los archivos",
                "listado de rutas y archivos",
                "lee el proyecto completo");
    }

    private boolean asksProjectStructure(String normalizedPrompt) {
        return containsAny(normalizedPrompt,
                "estructura del proyecto",
                "analiza la estructura",
                "analizar la estructura",
                "estructura de mi proyecto");
    }

    private boolean asksCapabilities(String normalizedPrompt) {
        return containsAny(normalizedPrompt,
                "que comandos puedes ejecutar",
                "que puedes hacer",
                "que comandos soportas",
                "que operaciones puedes hacer");
    }

    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String inferLanguage(String path) {
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "text";
        }
        return path.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n... [truncated]";
    }

    private String normalize(String text) {
        String lowered = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lowered, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static final class Resolution {
        private final String path;
        private final String message;

        private Resolution(String path, String message) {
            this.path = path;
            this.message = message;
        }

        private static Resolution withPath(String path) {
            return new Resolution(path, null);
        }

        private static Resolution withMessage(String message) {
            return new Resolution(null, message);
        }
    }
}