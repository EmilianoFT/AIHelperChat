package com.aihelper.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Centraliza el acceso al workspace de Eclipse.
 * 
 * IMPORTANTE:
 * - No accede al filesystem directamente
 * - Usa APIs oficiales de Eclipse
 * - Soporta archivos NO guardados
 */
public class WorkspaceService {

    /* ============================================================
     * NIVEL 1 (OBLIGATORIO)
     * Archivo activo en el editor (contexto principal para IA)
     * ============================================================ */

    /**
     * Devuelve el contenido actual del editor activo,
     * incluyendo cambios no guardados.
     */
    public String getActiveEditorContent() {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) {
            return "";
        }

        IDocument document = editor.getDocumentProvider()
                .getDocument(editor.getEditorInput());

        return document != null ? document.get() : "";
    }

    /**
     * Devuelve el nombre del archivo activo (UserService.java, etc)
     */
    public String getActiveEditorFileName() {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) {
            return "";
        }

        IEditorInput input = editor.getEditorInput();
        return input.getName();
    }

    /**
     * Devuelve la extensión del archivo activo (java, js, md, etc)
     */
    public String getActiveEditorFileExtension() {
        String name = getActiveEditorFileName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1) : "";
    }

    /**
     * Lista nombres y rutas relativas de los archivos actualmente abiertos en el Workbench.
     */
    public String listOpenFiles() {
        StringBuilder sb = new StringBuilder();
        try {
            if (!PlatformUI.isWorkbenchRunning()) {
                return "[Workbench no iniciado]";
            }
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null || window.getActivePage() == null) {
                return "[Sin ventana activa]";
            }
            IEditorReference[] refs = window.getActivePage().getEditorReferences();
            if (refs == null || refs.length == 0) {
                return "[Sin editores abiertos]";
            }
            Arrays.stream(refs).forEach(ref -> {
                try {
                    IEditorInput input = ref.getEditorInput();
                    String name = input.getName();
                    String path = "";
                    IFile file = adaptToFile(input);
                    if (file != null) {
                        path = file.getProjectRelativePath().toString();
                    }
                    sb.append(name);
                    if (!path.isEmpty()) {
                        sb.append(" (").append(path).append(")");
                    }
                    sb.append("\n");
                } catch (Exception ignored) {
                }
            });
            return sb.toString();
        } catch (Exception e) {
            return "[Error listando editores abiertos]";
        }
    }

    /**
     * Busca un término de texto simple en archivos de un proyecto (case-insensitive).
     * Limita el número de coincidencias para evitar respuestas enormes.
     */
    public String searchText(String projectName, String query, int maxMatches) {
        if (query == null || query.isBlank()) {
            return "[Término de búsqueda vacío]";
        }
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists() || !project.isOpen()) {
            return "[Proyecto no encontrado]";
        }

        String needle = query.toLowerCase();
        AtomicInteger remaining = new AtomicInteger(Math.max(1, maxMatches));
        StringBuilder sb = new StringBuilder();

        try {
            project.accept(resource -> {
                if (remaining.get() <= 0) return false;
                if (resource instanceof IFile file) {
                    if (shouldSkipFile(file)) return true;
                    String content = readFile(file);
                    if (content == null || content.isEmpty()) return true;
                    String lower = content.toLowerCase();
                    int idx = lower.indexOf(needle);
                    if (idx >= 0) {
                        remaining.decrementAndGet();
                        int line = content.substring(0, idx).split("\\R").length;
                        sb.append(file.getProjectRelativePath())
                          .append(":")
                          .append(line)
                          .append("\n");
                    }
                }
                return remaining.get() > 0;
            });
        } catch (Exception e) {
            return "[Error buscando en el proyecto]";
        }

        if (sb.length() == 0) {
            return "[Sin coincidencias]";
        }
        return sb.toString();
    }

    /**
     * Devuelve el nombre del proyecto del editor activo, o vacío si no hay.
     */
    public String getActiveProjectName() {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) {
            return "";
        }
        IEditorInput input = editor.getEditorInput();
        IFile file = adaptToFile(input);
        if (file != null && file.getProject() != null) {
            return file.getProject().getName();
        }
        return "";
    }

    /* ============================================================
     * NIVEL 2 (OPCIONAL)
     * Lectura de archivos guardados del proyecto
     * ============================================================ */

    /**
     * Lee un archivo guardado del workspace (NO incluye cambios no guardados).
     */
    public String readFile(IFile file) {
        try (InputStream is = file.getContents()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Lee un archivo por ruta relativa dentro de un proyecto.
     */
    public String readFile(String projectName, String projectRelativePath) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return "";
        }

        IFile file = project.getFile(projectRelativePath);
        if (!file.exists()) {
            return "";
        }

        return readFile(file);
    }

    /**
     * Lee un rango de líneas (1-based, inclusivo) de un archivo guardado del workspace.
     * Si los parámetros son inválidos o el archivo no existe, devuelve cadena vacía.
     */
    public String readFileRange(String projectName, String projectRelativePath, int startLine, int endLine) {
        String content = readFile(projectName, projectRelativePath);
        if (content == null || content.isBlank()) {
            return "";
        }
        String[] lines = content.split("\\R", -1);
        int from = Math.max(1, Math.min(startLine, lines.length));
        int to = Math.max(from, Math.min(endLine, lines.length));
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            sb.append(lines[i - 1]);
            if (i < to) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Lista la estructura de carpetas/archivos de un proyecto hasta cierta profundidad.
     */
    public String listProjectTree(String projectName, int maxDepth, int maxEntries) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return "[Proyecto no encontrado]";
        }

        int depthLimit = Math.max(1, maxDepth);
        int entryLimit = Math.max(1, maxEntries);
        StringBuilder sb = new StringBuilder();
        sb.append("Proyecto ").append(projectName).append("\n");

        try {
            appendTree(project, 0, depthLimit, new int[] { entryLimit }, sb, "");
        } catch (CoreException e) {
            sb.append("[Error listando archivos]\n");
        }

        return sb.toString();
    }

    private void appendTree(IContainer container,
                            int depth,
                            int maxDepth,
                            int[] remaining,
                            StringBuilder sb,
                            String indent) throws CoreException {
        if (remaining[0] <= 0 || depth >= maxDepth) {
            return;
        }

        for (IResource member : container.members()) {
            if (remaining[0] <= 0) {
                sb.append(indent).append("... (límite alcanzado)\n");
                return;
            }

            sb.append(indent)
              .append(member.getName());
            if (member instanceof IContainer) {
                sb.append("/");
            }
            sb.append("\n");
            remaining[0]--;

            if (member instanceof IContainer nested) {
                appendTree(nested,
                    depth + 1,
                    maxDepth,
                    remaining,
                    sb,
                    indent + "  ");
            }
        }
    }

    private boolean shouldSkipFile(IFile file) {
        if (file.isDerived()) {
            return true;
        }

        for (String segment : file.getProjectRelativePath().segments()) {
            if (EXCLUDED_FOLDERS.contains(segment)) {
                return true;
            }
        }

        String ext = file.getFileExtension();
        if (ext == null) {
            return false;
        }

        return BINARY_EXTENSIONS.contains(ext.toLowerCase());
    }

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        "class", "jar", "war", "ear", "dll", "exe", "so",
        "png", "jpg", "jpeg", "gif", "bmp", "ico",
        "pdf", "zip", "gz", "tgz", "xz", "7z",
        "mp3", "mp4", "mov", "avi", "wav",
        "ttf", "otf", "woff", "woff2", "eot",
        "pdb", "db", "sqlite", "pack"
    );

    private static final Set<String> EXCLUDED_FOLDERS = Set.of(
        "bin", "target", ".settings", ".metadata", ".git", ".idea", ".vscode", ".gradle", ".m2", "node_modules"
    );

    /* ============================================================
     * NIVEL 3 (AVANZADO / PESADO)
     * Snapshot del workspace completo (NO usar en cada prompt)
     * ============================================================ */

    /**
     * Lee TODOS los archivos de TODOS los proyectos abiertos.
     * ⚠️ USAR SOLO PARA INDEXADO O EMBEDDINGS.
     */
    public String readWorkspaceSnapshot() {
        StringBuilder sb = new StringBuilder();

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = root.getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            sb.append("\n=== Project: ")
              .append(project.getName())
              .append(" ===\n");

            try {
                project.accept(resource -> {
                    if (resource instanceof IFile file) {
                        if (shouldSkipFile(file)) {
                            return true;
                        }

                        sb.append("\nFile: ")
                          .append(file.getProjectRelativePath())
                          .append("\n");

                        try (InputStream is = file.getContents()) {
                            sb.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            sb.append("[Error reading file]\n");
                        }
                    }
                    return true;
                });
            } catch (Exception e) {
                sb.append("[Error reading project]\n");
            }
        }

        return sb.toString();
    }

    /* ============================================================
     * Helpers internos
     * ============================================================ */

    private ITextEditor getActiveTextEditor() {
        try {
            IEditorPart editor = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow()
                    .getActivePage()
                    .getActiveEditor();

            if (editor instanceof ITextEditor textEditor) {
                return textEditor;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Inserta o reemplaza la selección actual del editor activo con el texto dado.
     * Retorna true si pudo aplicar, false si no hay editor de texto activo.
     */
    public boolean applyToActiveEditor(String text) {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) {
            return false;
        }

        IDocument document = editor.getDocumentProvider()
                .getDocument(editor.getEditorInput());
        if (document == null) {
            return false;
        }

        ISelection selection = editor.getSelectionProvider() != null
                ? editor.getSelectionProvider().getSelection()
                : null;

        int offset = 0;
        int length = 0;

        if (selection instanceof ITextSelection ts) {
            offset = ts.getOffset();
            length = ts.getLength();
        }

        try {
            document.replace(offset, length, text);
            return true;
        } catch (BadLocationException e) {
            return false;
        }
    }

    public boolean applyFullToActiveEditor(String text) {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) {
            return false;
        }

        IDocument document = editor.getDocumentProvider()
                .getDocument(editor.getEditorInput());
        if (document == null) {
            return false;
        }

        try {
            document.replace(0, document.getLength(), text);
            return true;
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * Devuelve el texto actualmente seleccionado en el editor activo.
     */
    public String getActiveSelectionText() {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) return "";
        if (editor.getSelectionProvider() == null) return "";
        ISelection sel = editor.getSelectionProvider().getSelection();
        if (sel instanceof ITextSelection ts) {
            return ts.getText();
        }
        return "";
    }

    /**
     * Devuelve la ruta relativa al proyecto del editor activo, o vacío.
     */
    public String getActiveEditorProjectRelativePath() {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null) return "";
        IEditorInput input = editor.getEditorInput();
        IFile file = adaptToFile(input);
        if (file != null) {
            return file.getProjectRelativePath().toString();
        }
        return "";
    }

    private IFile adaptToFile(Object adaptable) {
        if (!(adaptable instanceof IAdaptable a)) {
            return null;
        }
        return a.getAdapter(IFile.class);
    }
}
