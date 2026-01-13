package com.aihelper.workspace;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class WorkspaceService {

    /* =======================
       WORKSPACE SNAPSHOT
       ======================= */

    public String readWorkspaceSnapshot() {
        StringBuilder sb = new StringBuilder();
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        try {
            for (IProject project : root.getProjects()) {
                if (!project.isOpen()) continue;
                sb.append("Project: ").append(project.getName()).append("\n");
                appendResources(project, sb, 1);
            }
        } catch (CoreException e) {
            sb.append("ERROR: ").append(e.getMessage());
        }

        return sb.toString();
    }

    private void appendResources(IResource resource, StringBuilder sb, int level) throws CoreException {
        for (IResource r : ((IProject) resource).members()) {
            indent(sb, level);
            sb.append(r.getName()).append("\n");
        }
    }

    private void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    /* =======================
       PROJECT TREE
       ======================= */

    public String listProjectTree(String projectName, int maxDepth, int maxFiles) {
        StringBuilder sb = new StringBuilder();
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return "Project not found or closed: " + projectName;
        }

        try {
            listTree(project, sb, 0, maxDepth, new int[]{0}, maxFiles);
        } catch (CoreException e) {
            return "ERROR: " + e.getMessage();
        }

        return sb.toString();
    }

    private void listTree(
            IResource resource,
            StringBuilder sb,
            int depth,
            int maxDepth,
            int[] counter,
            int maxFiles
    ) throws CoreException {

        if (depth > maxDepth || counter[0] >= maxFiles) return;

        if (resource instanceof IFile) {
            indent(sb, depth);
            sb.append(resource.getProjectRelativePath()).append("\n");
            counter[0]++;
            return;
        }

        if (resource instanceof IProject) {
            for (IResource r : ((IProject) resource).members()) {
                listTree(r, sb, depth + 1, maxDepth, counter, maxFiles);
            }
        }
    }

    /* =======================
       FILE READ
       ======================= */

    public String readFileRange(String projectName, String path, int startLine, int endLine) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

        if (!project.exists()) {
            return "Project not found: " + projectName;
        }

        IFile file = project.getFile(path);
        if (!file.exists()) {
            return "File not found: " + path;
        }

        StringBuilder sb = new StringBuilder();
        int line = 1;

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(file.getContents(), StandardCharsets.UTF_8))) {

            String l;
            while ((l = reader.readLine()) != null) {
                if (line >= startLine && line <= endLine) {
                    sb.append(line).append(": ").append(l).append("\n");
                }
                if (line > endLine) break;
                line++;
            }

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }

        return sb.toString();
    }

    /* =======================
       OPEN FILES
       ======================= */

    public List<String> listOpenFiles() {
        List<String> files = new ArrayList<>();

        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();

        if (page == null) return files;

        for (IEditorReference ref : page.getEditorReferences()) {
            try {
                IEditorInput input = ref.getEditorInput();
                files.add(input.getName());
            } catch (PartInitException e) {
                // ignore broken editors
            }
        }

        return files;
    }

    /* =======================
       SELECTION
       ======================= */

    public String getActiveSelectionText() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();

        if (page == null) return "";

        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof ITextEditor)) return "";

        ITextEditor textEditor = (ITextEditor) editor;

        ITextSelection selection =
                (ITextSelection) textEditor
                        .getSelectionProvider()
                        .getSelection();

        return selection.getText();
    }

    /* =======================
       SEARCH
       ======================= */

    public String searchText(String projectName, String text, int maxResults) {
        StringBuilder sb = new StringBuilder();
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return "Project not found or closed: " + projectName;
        }

        try {
            searchInProject(project, text, sb, new int[]{0}, maxResults);
        } catch (CoreException e) {
            return "ERROR: " + e.getMessage();
        }

        return sb.toString();
    }

    private void searchInProject(
            IResource resource,
            String text,
            StringBuilder sb,
            int[] counter,
            int maxResults
    ) throws CoreException {

        if (counter[0] >= maxResults) return;

        if (resource instanceof IFile) {
            IFile file = (IFile) resource;

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(file.getContents(), StandardCharsets.UTF_8))) {

                String line;
                int ln = 1;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(text)) {
                        sb.append(file.getProjectRelativePath())
                          .append(":")
                          .append(ln)
                          .append("\n");
                        counter[0]++;
                        if (counter[0] >= maxResults) return;
                    }
                    ln++;
                }

            } catch (Exception ignored) {}
        }

        if (resource instanceof IProject) {
            for (IResource r : ((IProject) resource).members()) {
                searchInProject(r, text, sb, counter, maxResults);
            }
        }
    }
    
    public String getActiveEditorContent() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();

        if (page == null) return "";

        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof ITextEditor)) return "";

        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider provider = textEditor.getDocumentProvider();
        IDocument document = provider.getDocument(textEditor.getEditorInput());

        return document != null ? document.get() : "";
    }
    
    public String getActiveEditorFileName() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();

        if (page == null) return "";

        IEditorPart editor = page.getActiveEditor();
        if (editor == null) return "";

        return editor.getEditorInput().getName();
    }
    
    public String getActiveEditorFileExtension() {
        String name = getActiveEditorFileName();
        int idx = name.lastIndexOf('.');
        return (idx > 0 && idx < name.length() - 1)
                ? name.substring(idx + 1)
                : "";
    }
    
    public String readFile(String projectName, String relativePath) {
        IFile file = ResourcesPlugin.getWorkspace()
                .getRoot()
                .getProject(projectName)
                .getFile(relativePath);

        if (!file.exists()) return "";

        StringBuilder sb = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getContents(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

        } catch (Exception e) {
            return "";
        }

        return sb.toString();
    }
    
    /**
     * Reemplaza todo el contenido del editor activo con el texto dado.
     * @param newContent El nuevo contenido para el editor activo.
     * @return true si se aplicó correctamente, false si no hay editor activo.
     */
    public boolean applyFullToActiveEditor(String newContent) {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();
        if (page == null) return false;
        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof ITextEditor)) return false;
        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider provider = textEditor.getDocumentProvider();
        IDocument document = provider.getDocument(textEditor.getEditorInput());
        if (document == null) return false;
        document.set(newContent != null ? newContent : "");
        return true;
    }
    
    /**
     * Devuelve el nombre del proyecto activo en el editor, o "" si no hay ninguno.
     */
    public String getActiveProjectName() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage();
        if (page == null) return "";
        IEditorPart editor = page.getActiveEditor();
        if (editor == null) return "";
        IEditorInput input = editor.getEditorInput();
        IFile file = input.getAdapter(IFile.class);
        if (file != null && file.getProject() != null) {
            return file.getProject().getName();
        }
        return "";
    }
}
