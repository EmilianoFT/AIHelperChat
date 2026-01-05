package com.aihelper.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class ApplyChangesDialog extends Dialog {

    private final String diff;

    public ApplyChangesDialog(Shell parentShell, String diff) {
        super(parentShell);
        this.diff = diff == null ? "" : diff;
    }

    public static boolean confirm(Shell shell, String diff) {
        ApplyChangesDialog dialog = new ApplyChangesDialog(shell, diff);
        return dialog.open() == IDialogConstants.OK_ID;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        StyledText text = new StyledText(container, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        text.setWordWrap(false);

        applyDiff(text, diff);

        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Apply", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(720, 480);
    }

    private void applyDiff(StyledText text, String content) {
        if (content == null) return;
        text.setText(content);

        Color green = text.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
        Color red = text.getDisplay().getSystemColor(SWT.COLOR_DARK_RED);
        Color gray = text.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);

        int offset = 0;
        String[] lines = content.split("\n", -1);
        for (String line : lines) {
            int len = line.length() + 1; // include newline
            if (line.startsWith("+")) {
                setRange(text, offset, len, green);
            } else if (line.startsWith("-")) {
                setRange(text, offset, len, red);
            } else if (line.startsWith("@@") || line.startsWith("---") || line.startsWith("+++")) {
                setRange(text, offset, len, gray);
            }
            offset += len;
        }
    }

    private void setRange(StyledText text, int start, int length, Color color) {
        StyleRange range = new StyleRange();
        range.start = start;
        range.length = Math.max(0, length - 1);
        range.foreground = color;
        text.setStyleRange(range);
    }
}