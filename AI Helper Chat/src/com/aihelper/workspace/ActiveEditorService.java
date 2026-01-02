package com.aihelper.workspace;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public class ActiveEditorService {

    public String getActiveFileContent() {
        try {
            IEditorPart editor = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow()
                    .getActivePage()
                    .getActiveEditor();

            if (editor instanceof ITextEditor textEditor) {
                IDocument doc = textEditor
                        .getDocumentProvider()
                        .getDocument(textEditor.getEditorInput());
                return doc.get();
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }
}