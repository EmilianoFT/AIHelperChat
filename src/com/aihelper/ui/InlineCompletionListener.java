package com.aihelper.ui;


import org.eclipse.jface.text.*;

public class InlineCompletionListener implements ITextListener {

    @Override
    public void textChanged(TextEvent event) {
        if (event.getText() != null && event.getText().length() > 0) {
            // Trigger AI completion
            // Insert ghost text (StyledText)
        }
    }
}
