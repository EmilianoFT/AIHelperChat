package com.aihelper.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
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
}
